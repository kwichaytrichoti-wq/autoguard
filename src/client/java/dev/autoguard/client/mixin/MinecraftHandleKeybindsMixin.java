package dev.autoguard.client.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import dev.autoguard.client.AutoGuardClient;

/**
 * Cancels vanilla's automatic per-tick release of the item in use ONLY while AutoGuard owns
 * the shield. Vanilla's Minecraft.handleKeybinds() releases the active item every tick when
 * the use key is not physically down. Since AutoGuard starts shield use via gameMode.useItem()
 * without holding the use key, vanilla would release the shield one tick after every raise.
 *
 * The mod's own release path sets vanillaReleaseBypass = true so the Mixin lets the release
 * through, then resets it to false.
 */
@Mixin(Minecraft.class)
public class MinecraftHandleKeybindsMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoGuard");
    private static boolean logged = false;

    static {
        // Log once when the mixin class loads, proving the hook is registered.
        LOGGER.info("AutoGuard mixin MinecraftHandleKeybindsMixin loaded: redirecting handleKeybinds releaseUsingItem");
    }

    @Redirect(
        method = "handleKeybinds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/client/player/LocalPlayer;)V"
        )
    )
    private void onVanillaReleaseUsingItem(MultiPlayerGameMode gameMode, LocalPlayer player) {
        if (!logged) {
            logged = true;
            LOGGER.info("AutoGuard handleKeybinds redirect active: vanilla releaseUsingItem intercepted");
        }
        // Let vanilla release only if AutoGuard does not own the shield, or the mod itself
        // requested the release (bypass flag set). Otherwise skip: AutoGuard manages release.
        if (!AutoGuardClient.ownsShield() || AutoGuardClient.shouldAllowVanillaRelease()) {
            gameMode.releaseUsingItem(player);
        }
    }
}
