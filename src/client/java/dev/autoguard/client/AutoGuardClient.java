package dev.autoguard.client;

import dev.autoguard.client.mixin.KeyMappingClickAccessor;
import dev.autoguard.config.GuardConfig;
import dev.autoguard.detect.ThreatDetector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Client-only shield controller; all raising goes through vanilla's use-item interaction.
 *
 * 1.0.2 fixes (verified against real 26.3 source):
 *   - Removed every keyUse.setDown(...) call and the realKeyUseDown field. keyUse.isDown()
 *     now reads the real player state. A Mixin on Minecraft.handleKeybinds cancels vanilla's
 *     automatic per-tick release of the item in use ONLY while AutoGuard owns the shield, so
 *     the shield is not released one tick after every raise.
 *   - Duty cycle: max continuous hold, cooldown after release, hold after threat gone.
 *   - Attack priority: detects pending attack clicks (via KeyMapping clickCount accessor,
 *     without consuming them) and releases the shield BEFORE vanilla input handling so the
 *     click becomes a real attack. Suppresses re-raise after any attack.
 *   - Failsafes: force-release after max hold + grace; clear all state on world leave, death,
 *     screen open.
 *   - Debug: /autoguard debug on prints every RAISE/RELEASE with reason to chat and latest.log.
 */
public final class AutoGuardClient implements ClientModInitializer {
    public static GuardConfig CONFIG;
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoGuard");
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static KeyMapping toggleKey;
    private static boolean raisedByMod;
    private static InteractionHand raisedHand;
    private static Phase phase = Phase.IDLE;
    private static long noThreatSince = -1L;
    private static long retryAfterTick;           // cooldown: do not re-raise until this tick
    private static long maxHoldUntilTick;         // max continuous hold deadline
    private static long attackSuppressUntilTick;  // suppress re-raise after an attack
    private static boolean vanillaReleaseBypass;  // set by mod's own release so the Mixin lets it through
    private static boolean debug;

    private enum Phase { IDLE, RAISING, HOLDING, RELEASING }

    /** Whether the shield use currently active was started by this mod. */
    public static boolean ownsShield() {
        return raisedByMod;
    }

    /** Called by the Mixin to decide whether to let vanilla release the item in use. */
    public static boolean shouldAllowVanillaRelease() {
        return vanillaReleaseBypass;
    }

    @Override
    public void onInitializeClient() {
        CONFIG = GuardConfig.load();
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autoguard.toggle", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
        ClientTickEvents.START_CLIENT_TICK.register(AutoGuardClient::yieldForPlayerInput);
        ClientTickEvents.END_CLIENT_TICK.register(AutoGuardClient::tick);
        registerCommands();
        registerAttackPriority();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("autoguard", "status"),
                (graphics, tickCounter) -> renderHud(graphics));
    }

    /**
     * Runs at the start of each client tick, before Minecraft.handleKeybinds.
     * Handles pending attack clicks (release shield so the attack lands),
     * screen-open release, and player input priority.
     */
    private static void yieldForPlayerInput(Minecraft client) {
        // Release immediately if a screen opened while we own the shield.
        // 26.3: Minecraft.screen moved to Minecraft.gui.screen().
        if (client.gui != null && client.gui.screen() != null) {
            releaseIfOwned(client, true);
            return;
        }

        LocalPlayer player = client.player;
        if (player == null) {
            releaseIfOwned(client, true);
            return;
        }

        // Attack priority: detect a pending attack click WITHOUT consuming it.
        // If the player tapped attack (including quick taps from touch buttons) while we
        // hold the shield, release first so vanilla's handleKeybinds processes the click
        // as a real attack in the same tick.
        if (raisedByMod && clickPending(client.options.keyAttack)) {
            releaseIfOwned(client, true);
            long tick = player.tickCount;
            attackSuppressUntilTick = tick + Math.round(CONFIG.attackSuppressSeconds() * 20.0);
            retryAfterTick = tick + Math.round(CONFIG.cooldownAfterReleaseSeconds() * 20.0);
            maxHoldUntilTick = 0L;
            debug("RELEASE attack-priority", player);
            return;
        }

        // Player's own use key takes priority over our shield (e.g. eating with shield in off-hand).
        if (shouldYieldToPlayerInput(player)) {
            if (raisedByMod) {
                releaseIfOwned(client, true);
                retryAfterTick = player.tickCount + Math.round(CONFIG.cooldownAfterReleaseSeconds() * 20.0);
                debug("RELEASE player-input", player);
            }
        }
    }

    /**
     * Reads the raw click counter without consuming it, via the Mixin accessor.
     * Returns true if a click is queued (e.g. a touch attack tap) but not yet processed.
     */
    private static boolean clickPending(KeyMapping key) {
        return ((KeyMappingClickAccessor) key).autoguard$getClickCount() > 0;
    }

    private static boolean shouldYieldToPlayerInput(Player player) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.keyAttack.isDown()) return true;
        if (CONFIG.playerInputPriority && client.options.keyUse.isDown() && raisedHand == InteractionHand.OFF_HAND) return true;
        return false;
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            releaseIfOwned(client, true);
            return;
        }

        LocalPlayer player = client.player;

        if (toggleKey.consumeClick()) {
            CONFIG.enabled = !CONFIG.enabled;
            CONFIG.save();
            feedback("AutoGuard " + (CONFIG.enabled ? "on" : "off"));
        }

        // World/death failsafe: release and clear when not in a playable state.
        // Covers death, respawn, dimension change, world leave (level == null handled above).
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.isFallFlying()) {
            releaseIfOwned(client, true);
            return;
        }

        if (!CONFIG.enabled || !environmentAllowed(client, player)) {
            releaseIfOwned(client, true);
            return;
        }

        InteractionHand shieldHand = chooseShieldHand(player);
        if (shieldHand == null) {
            releaseIfOwned(client, true);
            return;
        }

        ItemStack shield = player.getItemInHand(shieldHand);
        int durability = shield.isDamageableItem() ? shield.getMaxDamage() - shield.getDamageValue() : Integer.MAX_VALUE;
        if (durability <= CONFIG.minimumDurability) {
            releaseIfOwned(client, true);
            return;
        }

        if (player.getCooldowns().isOnCooldown(shield)) {
            releaseIfOwned(client, true);
            return;
        }

        // Safety: if we think we own the shield but the player is no longer using it,
        // ownership is stale (e.g. respawn, dimension change). Clear it.
        if (raisedByMod && !stillOwnsUse(player)) {
            clearOwnership();
        }

        boolean threat;
        try {
            threat = ThreatDetector.hasThreat(client.level, player, CONFIG);
        } catch (Throwable error) {
            LOGGER.error("AutoGuard threat scan failed; releasing its shield safely", error);
            releaseIfOwned(client, true);
            return;
        }

        // If the player is manually attacking or using an item, don't interfere.
        if (shouldYieldToPlayerInput(player)) {
            if (raisedByMod) releaseIfOwned(client, true);
            return;
        }

        if (threat) {
            noThreatSince = -1L;
            if (raisedByMod) {
                phase = Phase.HOLDING;
                // Failsafe: force release if we have held the shield continuously too long.
                if (player.tickCount >= maxHoldUntilTick) {
                    releaseIfOwned(client, true);
                    retryAfterTick = player.tickCount + Math.round(CONFIG.cooldownAfterReleaseSeconds() * 20.0);
                    debug("RELEASE max-hold", player);
                }
                return;
            }
            // Not raised yet. Respect cooldowns and attack suppression.
            if (player.tickCount < retryAfterTick) return;
            if (player.tickCount < attackSuppressUntilTick) return;
            // Never take over a manually raised shield. Respect any other active item use too.
            if (player.isUsingItem() && (player.getUsedItemHand() == shieldHand || CONFIG.dontInterruptItemUse)) return;

            phase = Phase.RAISING;
            raisedHand = shieldHand;
            maxHoldUntilTick = player.tickCount + Math.round(CONFIG.maxContinuousHoldSeconds() * 20.0);
            try {
                client.gameMode.useItem(player, shieldHand);
                if (player.isUsingItem() && player.getUsedItemHand() == shieldHand) {
                    raisedByMod = true;
                    retryAfterTick = 0L;
                    phase = Phase.HOLDING;
                    debug("RAISE threat", player);
                } else {
                    clearOwnership();
                    retryAfterTick = player.tickCount + 5L;
                }
            } catch (Throwable error) {
                LOGGER.warn("AutoGuard could not start vanilla shield use", error);
                clearOwnership();
            }
            return;
        }

        // No threat.
        if (raisedByMod) {
            if (noThreatSince < 0L) noThreatSince = player.tickCount;
            long holdTicks = Math.round(CONFIG.holdAfterThreatGoneSeconds() * 20.0);
            if (player.tickCount - noThreatSince >= holdTicks) {
                releaseIfOwned(client, true);
                retryAfterTick = player.tickCount + Math.round(CONFIG.cooldownAfterReleaseSeconds() * 20.0);
                debug("RELEASE threat-gone", player);
            }
        }
    }

    private static boolean environmentAllowed(Minecraft client, Player player) {
        if (client.hasSingleplayerServer()) return true;
        return CONFIG.allowRemoteServers;
    }

    private static InteractionHand chooseShieldHand(Player player) {
        boolean main = player.getMainHandItem().is(Items.SHIELD);
        boolean off = player.getOffhandItem().is(Items.SHIELD);
        if (off) return InteractionHand.OFF_HAND;
        if (main) return InteractionHand.MAIN_HAND;
        return null;
    }

    private static boolean stillOwnsUse(Player player) {
        if (raisedHand == null || !player.getItemInHand(raisedHand).is(Items.SHIELD)) return false;
        return player.isUsingItem() && player.getUsedItemHand() == raisedHand;
    }

    private static void releaseIfOwned(Minecraft client, boolean stopVanillaUse) {
        if (raisedByMod) {
            phase = Phase.RELEASING;
            vanillaReleaseBypass = true; // let the Mixin pass our release through
            try {
                if (client.player != null && stopVanillaUse && client.gameMode != null
                        && raisedHand != null && client.player.isUsingItem()
                        && client.player.getUsedItemHand() == raisedHand) {
                    client.gameMode.releaseUsingItem(client.player);
                    client.player.stopUsingItem();
                }
            } catch (Throwable error) {
                LOGGER.warn("AutoGuard could not release its shield use cleanly", error);
                if (client.player != null) client.player.stopUsingItem();
            } finally {
                vanillaReleaseBypass = false;
            }
            debug("RELEASE", client.player);
        }
        clearOwnership();
    }

    private static void clearOwnership() {
        raisedByMod = false;
        raisedHand = null;
        noThreatSince = -1L;
        retryAfterTick = 0L;
        maxHoldUntilTick = 0L;
        attackSuppressUntilTick = 0L;
        phase = Phase.IDLE;
    }

    private static void registerAttackPriority() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof LocalPlayer) yieldForAttack();
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof LocalPlayer) yieldForAttack();
            return InteractionResult.PASS;
        });
    }

    private static void yieldForAttack() {
        long tick = CLIENT.player == null ? 0L : CLIENT.player.tickCount;
        if (raisedByMod) releaseIfOwned(CLIENT, true);
        retryAfterTick = tick + 1L;
        attackSuppressUntilTick = tick + Math.round(CONFIG.attackSuppressSeconds() * 20.0);
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("autoguard")
                            .then(literal("on").executes(context -> setEnabled(true)))
                            .then(literal("off").executes(context -> setEnabled(false)))
                            .then(literal("toggle").executes(context -> setEnabled(!CONFIG.enabled)))
                            .then(literal("status").executes(context -> {
                                feedback("AutoGuard " + (CONFIG.enabled ? "on" : "off") + " | "
                                        + (raisedByMod ? "shield raised by mod" : "shield not raised by mod")
                                        + " | preset " + CONFIG.preset);
                                return 1;
                            }))
                            .then(literal("reload").executes(context -> {
                                CONFIG = GuardConfig.load();
                                feedback("AutoGuard config reloaded.");
                                return 1;
                            }))
                            .then(literal("preset")
                                    .then(literal("cautious").executes(context -> setPreset("Cautious")))
                                    .then(literal("normal").executes(context -> setPreset("Normal")))
                                    .then(literal("aggressive").executes(context -> setPreset("Aggressive"))))
                            .then(literal("debug")
                                    .then(literal("on").executes(context -> {
                                        debug = true;
                                        feedback("AutoGuard debug on.");
                                        return 1;
                                    }))
                                    .then(literal("off").executes(context -> {
                                        debug = false;
                                        feedback("AutoGuard debug off.");
                                        return 1;
                                    })))
            );
        });
    }

    private static int setEnabled(boolean value) {
        CONFIG.enabled = value;
        CONFIG.save();
        if (!value) releaseIfOwned(CLIENT, true);
        feedback("AutoGuard " + (value ? "on" : "off"));
        return 1;
    }

    private static int setPreset(String value) {
        CONFIG.setPreset(value);
        feedback("AutoGuard preset: " + value);
        return 1;
    }

    private static void feedback(String message) {
        if (CLIENT.player != null) CLIENT.player.sendOverlayMessage(Component.literal(message));
    }

    private static void debug(String event, Player player) {
        if (!debug || player == null) return;
        InteractionHand hand = player.getUsedItemHand();
        String msg = String.format("AutoGuard debug[%s]: using=%s blocking=%s hand=%s tick=%d",
                event, player.isUsingItem(), player.isBlocking(), hand == null ? "-" : hand, player.tickCount);
        feedback(msg);
        LOGGER.info(msg);
    }

    private static void renderHud(GuiGraphicsExtractor graphics) {
        if (!CONFIG.showHud || CLIENT.player == null) return;
        String text;
        if (!CONFIG.enabled) {
            text = "AutoGuard: OFF";
        } else if (raisedByMod && CLIENT.player.isBlocking()) {
            text = "AutoGuard: SHIELD UP";
        } else if (raisedByMod) {
            text = "AutoGuard: raising...";
        } else {
            text = "AutoGuard: ON";
        }
        int x = CONFIG.hudPosition.endsWith("RIGHT") ? CLIENT.getWindow().getGuiScaledWidth() - CLIENT.font.width(text) - 6 : 6;
        int y = CONFIG.hudPosition.startsWith("BOTTOM") ? CLIENT.getWindow().getGuiScaledHeight() - 14 : 6;
        graphics.text(CLIENT.font, text, x, y, 0xFFFFFFFF, true);
    }
}
