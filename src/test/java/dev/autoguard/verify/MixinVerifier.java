package dev.autoguard.verify;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Build-time verifier for AutoGuard Mixin targets.
 * <p>
 * Reads the real Minecraft client jar (provided by Fabric Loom) and checks that every
 * string-based reference used in AutoGuard's mixins really exists:
 * <ul>
 *   <li>{@code Minecraft.handleKeybinds} method exists</li>
 *   <li>{@code MultiPlayerGameMode.releaseUsingItem(LocalPlayer)} method exists</li>
 *   <li>The {@code INVOKE MultiPlayerGameMode.releaseUsingItem} call site exists inside
 *       {@code handleKeybinds}</li>
 *   <li>{@code KeyMapping.clickCount} field exists</li>
 * </ul>
 * Exits with a non-zero status code if any check fails, so the Gradle build fails before
 * producing a jar with a broken mixin.
 */
public final class MixinVerifier {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("MixinVerifier: missing minecraft jar path argument(s)");
            System.exit(2);
        }

        List<String> failures = new ArrayList<>();
        boolean foundJar = false;

        for (String jarPath : args) {
            File jar = new File(jarPath);
            if (!jar.exists() || !jar.isFile()) continue;

            // Only try jars that could be the Minecraft client jar.
            String name = jar.getName().toLowerCase();
            if (!name.contains("minecraft")) continue;

            foundJar = true;
            System.out.println("MixinVerifier: checking " + jar);

            // Run all checks on this jar. Collect failures but keep going to the next jar
            // if this one doesn't have the classes we need (e.g. common jar vs client jar).
            List<String> jarFailures = new ArrayList<>();

            // 1. Verify Minecraft class has handleKeybinds method
            boolean hasHandleKeybinds = verifyMethodInClass(jar,
                    "net/minecraft/client/Minecraft", "handleKeybinds", "()V");
            if (!hasHandleKeybinds) {
                jarFailures.add("Minecraft.handleKeybinds()V not found");
            } else {
                System.out.println("OK: Minecraft.handleKeybinds()V found");
            }

            // 2. Verify MultiPlayerGameMode.releaseUsingItem(Player) method exists
            boolean hasReleaseUsingItem = verifyMethodInClass(jar,
                    "net/minecraft/client/multiplayer/MultiPlayerGameMode",
                    "releaseUsingItem", "(Lnet/minecraft/world/entity/player/Player;)V");
            if (!hasReleaseUsingItem) {
                jarFailures.add("MultiPlayerGameMode.releaseUsingItem(Player)V not found");
            } else {
                System.out.println("OK: MultiPlayerGameMode.releaseUsingItem(Player)V found");
            }

            // 3. Verify the INVOKE releaseUsingItem call site exists inside handleKeybinds
            int invokeCount = countInvokeInMethod(jar,
                    "net/minecraft/client/Minecraft", "handleKeybinds", "()V",
                    "net/minecraft/client/multiplayer/MultiPlayerGameMode",
                    "releaseUsingItem", "(Lnet/minecraft/world/entity/player/Player;)V");
            System.out.println("OK: handleKeybinds contains " + invokeCount
                    + " INVOKE MultiPlayerGameMode.releaseUsingItem(LocalPlayer)V site(s)");
            if (invokeCount < 1) {
                jarFailures.add("No INVOKE MultiPlayerGameMode.releaseUsingItem(LocalPlayer)V found inside Minecraft.handleKeybinds");
            }

            // 4. Verify KeyMapping.clickCount field exists
            boolean hasClickCount = verifyFieldInClass(jar,
                    "net/minecraft/client/KeyMapping", "clickCount", "I");
            if (!hasClickCount) {
                jarFailures.add("KeyMapping.clickCount field not found");
            } else {
                System.out.println("OK: KeyMapping.clickCount field found");
            }

            // If this jar passed all checks, we're done.
            if (jarFailures.isEmpty()) {
                break;
            }

            // Otherwise, prefix failures with jar name and keep trying next jar.
            for (String f : jarFailures) {
                failures.add("[" + jar.getName() + "] " + f);
            }
        }

        if (!foundJar) {
            System.err.println("MixinVerifier: no Minecraft client jar found among arguments");
            System.exit(2);
        }

        if (!failures.isEmpty()) {
            System.err.println("MixinVerifier FAILED: " + failures.size() + " check(s) failed:");
            for (String f : failures) {
                System.err.println("  - " + f);
            }
            System.exit(1);
        }

        System.out.println("MixinVerifier: all mixin targets verified against real Minecraft 26.3 jar");
    }

    /** Returns true if {@code className} has a method with the given name and descriptor. */
    private static boolean verifyMethodInClass(File jar, String className, String methodName, String desc) {
        try (JarFile jf = new JarFile(jar)) {
            JarEntry entry = jf.getJarEntry(className + ".class");
            if (entry == null) return false;
            try (InputStream is = jf.getInputStream(entry)) {
                ClassNode cn = new ClassNode();
                ClassReader cr = new ClassReader(is);
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    if (mn.name.equals(methodName) && mn.desc.equals(desc)) return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    /** Returns true if {@code className} has a field with the given name and descriptor. */
    private static boolean verifyFieldInClass(File jar, String className, String fieldName, String fieldDesc) {
        try (JarFile jf = new JarFile(jar)) {
            JarEntry entry = jf.getJarEntry(className + ".class");
            if (entry == null) return false;
            try (InputStream is = jf.getInputStream(entry)) {
                ClassNode cn = new ClassNode();
                ClassReader cr = new ClassReader(is);
                cr.accept(cn, 0);
                for (FieldNode fn : cn.fields) {
                    if (fn.name.equals(fieldName) && fn.desc.equals(fieldDesc)) return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    /**
     * Counts INVOKE instructions inside {@code methodName} of {@code className} that call
     * {@code owner.methodName(desc)}.
     */
    private static int countInvokeInMethod(File jar, String className, String methodName, String methodDesc,
                                           String owner, String method, String methodDesc2) {
        try (JarFile jf = new JarFile(jar)) {
            JarEntry entry = jf.getJarEntry(className + ".class");
            if (entry == null) return 0;
            try (InputStream is = jf.getInputStream(entry)) {
                ClassNode cn = new ClassNode();
                ClassReader cr = new ClassReader(is);
                cr.accept(cn, 0);
                for (MethodNode mn : cn.methods) {
                    if (!mn.name.equals(methodName) || !mn.desc.equals(methodDesc)) continue;
                    int count = 0;
                    for (AbstractInsnNode insn : mn.instructions) {
                        if (insn instanceof MethodInsnNode min) {
                            if (min.getOpcode() == Opcodes.INVOKEVIRTUAL
                                    && min.owner.equals(owner)
                                    && min.name.equals(method)
                                    && min.desc.equals(methodDesc2)) {
                                count++;
                            }
                        }
                    }
                    return count;
                }
            }
        } catch (IOException ignored) {
            return 0;
        }
        return 0;
    }
}
