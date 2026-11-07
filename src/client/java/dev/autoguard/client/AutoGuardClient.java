package dev.autoguard.client;

import dev.autoguard.client.mixin.KeyMappingClickAccessor;
import dev.autoguard.config.GuardConfig;
import dev.autoguard.detect.ThreatDetector;
import dev.autoguard.detect.ThreatDetector.RangedWindUp;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Client-only shield controller; all raising goes through vanilla's use-item interaction.
 *
 * 1.0.5 behavior changes:
 *   - Protection range: maxReactionDistance (default 4.5 blocks) is a hard cap on melee threat
 *     distance; no melee threat counts beyond it regardless of lead-ticks estimate.
 *   - Ranged wind-up detection: skeletons, strays, bogged, pillagers, piglins (crossbow) and
 *     drowned (trident) drawing their weapon trigger a raise when inside the front arc,
 *     looking at the player, within rangedReactionDistance (default 20 blocks), and drawing
 *     long enough for the shield to be fully up before the shot leaves.
 *   - In-flight projectile detection kept as a second layer; shield stays up while a projectile
 *     is in flight plus hold-after-threat-gone time.
 *   - Dead settings fixed: resumeDelaySeconds now controls re-raise after player attack input
 *     ends; holdAfterThreatGoneSeconds uses its config value instead of hard-coded 0.2 s.
 *   - Damage report separates arrow hits from melee hits: arrows blocked, arrows not blocked,
 *     and for each unblocked arrow prints ticksSinceRaise, wind-up detected, distance to shooter,
 *     shooter inside arc. /autoguard stats reflects the split.
 *   - All 1.0.4 behavior kept: attack-driven rest, ~15s failsafe, speed-aware raising, pet skip,
 *     screen-open release, debug logging, verifyMixins build-time check.
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
    private static long retryAfterTick;           // resume-delay: do not re-raise until this tick
    private static long attackInputUntilTick;     // keep shield down while attack input active
    private static long maxHoldUntilTick;         // hidden failsafe: ~15 s continuous hold
    private static boolean vanillaReleaseBypass;  // set by mod's own release so the Mixin lets it through
    private static boolean debug;

    // Damage report / stats tracking.
    private static float lastHealth = -1.0f;
    private static long raiseStartTick = -1L;
    private static long lastAttackTick = -1L;
    private static int hitsWhileBlocking = 0;
    private static int hitsWhileNotBlocking = 0;
    private static int shieldRaises = 0;
    private static int releasesForAttack = 0;
    // Arrow vs melee separation.
    private static int arrowsBlocked = 0;
    private static int arrowsNotBlocked = 0;
    private static long lastRangedWindUpTick = -1L;
    private static double lastRangedWindUpDistance = -1.0;
    private static boolean lastRangedWindUpInsideArc = false;

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
        if (client.gui != null && client.gui.screen() != null) {
            releaseIfOwned(client, true, "screen-open");
            return;
        }

        LocalPlayer player = client.player;
        if (player == null) {
            releaseIfOwned(client, true, "no-player");
            return;
        }

        // Attack-driven rest: detect a pending attack click WITHOUT consuming it.
        if (raisedByMod && clickPending(client.options.keyAttack)) {
            releaseIfOwned(client, true, "attack-priority");
            long tick = player.tickCount;
            lastAttackTick = tick;
            // Keep shield down for resumeDelaySeconds after the attack click ends.
            long resumeDelayTicks = Math.round(CONFIG.resumeDelaySeconds() * 20.0);
            attackInputUntilTick = tick + resumeDelayTicks;
            releasesForAttack++;
            return;
        }

        // While attack input is active (key held or clicks pending), keep the shield down.
        if (player.tickCount < attackInputUntilTick) {
            if (raisedByMod) {
                releaseIfOwned(client, true, "attack-active");
            }
            return;
        }

        // Player's own use key takes priority over our shield (e.g. eating with shield in off-hand).
        if (shouldYieldToPlayerInput(player)) {
            if (raisedByMod) {
                releaseIfOwned(client, true, "player-input");
            }
            return;
        }
    }

    /** Reads the raw click counter without consuming it, via the Mixin accessor. */
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
            releaseIfOwned(client, true, "world-leave");
            return;
        }

        LocalPlayer player = client.player;

        if (toggleKey.consumeClick()) {
            CONFIG.enabled = !CONFIG.enabled;
            CONFIG.save();
            feedback("AutoGuard " + (CONFIG.enabled ? "on" : "off"));
        }

        // World/death failsafe: release and clear when not in a playable state.
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.isFallFlying()) {
            releaseIfOwned(client, true, "death-or-spectator");
            return;
        }

        if (!CONFIG.enabled || !environmentAllowed(client, player)) {
            releaseIfOwned(client, true, "disabled");
            return;
        }

        InteractionHand shieldHand = chooseShieldHand(player);
        if (shieldHand == null) {
            releaseIfOwned(client, true, "no-shield");
            return;
        }

        ItemStack shield = player.getItemInHand(shieldHand);
        int durability = shield.isDamageableItem() ? shield.getMaxDamage() - shield.getDamageValue() : Integer.MAX_VALUE;
        if (durability <= CONFIG.minimumDurability) {
            releaseIfOwned(client, true, "low-durability");
            return;
        }

        if (player.getCooldowns().isOnCooldown(shield)) {
            releaseIfOwned(client, true, "cooldown");
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
            releaseIfOwned(client, true, "scan-error");
            return;
        }

        // Track ranged wind-up state for the damage report (even when not raising).
        updateRangedWindUpTracking(client.level, player, CONFIG);

        // Damage report: detect health decrease and log when debug is on.
        checkDamageReport(player);

        // If the player is manually attacking or using an item, don't interfere.
        if (shouldYieldToPlayerInput(player)) {
            if (raisedByMod) releaseIfOwned(client, true, "player-input");
            return;
        }

        if (threat) {
            noThreatSince = -1L;
            if (raisedByMod) {
                phase = Phase.HOLDING;
                // Hidden failsafe: force release after ~15 s of continuous hold so a stuck
                // shield can never happen.
                if (player.tickCount >= maxHoldUntilTick) {
                    releaseIfOwned(client, true, "max-hold-failsafe");
                    debug("RELEASE max-hold-failsafe", player);
                }
                return;
            }
            // Not raised yet. Respect resume delay after attack input.
            if (player.tickCount < retryAfterTick) return;
            // Never take over a manually raised shield. Respect any other active item use too.
            if (player.isUsingItem() && (player.getUsedItemHand() == shieldHand || CONFIG.dontInterruptItemUse)) return;

            phase = Phase.RAISING;
            raisedHand = shieldHand;
            // Hidden failsafe deadline: ~15 seconds of continuous hold.
            maxHoldUntilTick = player.tickCount + 15L * 20L;
            try {
                client.gameMode.useItem(player, shieldHand);
                if (player.isUsingItem() && player.getUsedItemHand() == shieldHand) {
                    raisedByMod = true;
                    raiseStartTick = player.tickCount;
                    shieldRaises++;
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

        // No threat: release after holdAfterThreatGoneSeconds (configurable, default 0.2 s).
        if (raisedByMod) {
            if (noThreatSince < 0L) noThreatSince = player.tickCount;
            long holdTicks = Math.round(CONFIG.holdAfterThreatGoneSeconds() * 20.0);
            if (player.tickCount - noThreatSince >= holdTicks) {
                releaseIfOwned(client, true, "threat-gone");
                debug("RELEASE threat-gone", player);
            }
        }
    }

    /**
     * Track ranged wind-up state for the damage report. Called every tick while in-game.
     */
    private static void updateRangedWindUpTracking(Level level, Player player, GuardConfig config) {
        RangedWindUp windUp = ThreatDetector.getRangedWindUp(level, player, config);
        if (windUp != null) {
            lastRangedWindUpTick = player.tickCount;
            lastRangedWindUpDistance = windUp.distance;
            lastRangedWindUpInsideArc = windUp.insideArc;
            if (windUp.shouldRaise && !windUp.insideArc && debug) {
                debug("Ranged attacker aiming from outside arc: dist=" + String.format("%.1f", windUp.distance)
                        + " ticksDrawing=" + windUp.ticksDrawing + "/" + windUp.fullDrawTicks, player);
            }
        }
    }

    /**
     * Detect a decrease in the player's health and, when debug is on, print a damage report
     * to chat and latest.log. Also update the stats counters, separating arrow hits from melee hits.
     */
    private static void checkDamageReport(Player player) {
        float health = player.getHealth();
        if (lastHealth < 0.0f) {
            lastHealth = health;
            return;
        }
        if (health < lastHealth) {
            // The player took damage this tick.
            boolean blocking = raisedByMod && player.isBlocking();
            long ticksSinceRaise = raiseStartTick >= 0 ? player.tickCount - raiseStartTick : -1L;
            double nearestDistance = -1.0;
            try {
                nearestDistance = ThreatDetector.nearestThreatDistance(CLIENT.level, player, CONFIG);
            } catch (Throwable ignored) {
                // ignore
            }
            long ticksSinceAttack = lastAttackTick >= 0 ? player.tickCount - lastAttackTick : -1L;

            // Classify the hit: arrow (ranged projectile) vs melee.
            boolean isArrow = classifyDamageAsArrow(player);
            Entity shooter = null;
            double shooterDistance = -1.0;
            boolean shooterInsideArc = false;
            boolean windUpDetected = false;
            if (isArrow) {
                DamageSource source = player.getLastDamageSource();
                if (source != null) {
                    Entity direct = source.getEntity();
                    if (direct instanceof Projectile projectile) {
                        shooter = projectile.getOwner(); // the entity that shot the arrow
                    } else {
                        shooter = direct;
                    }
                }
                if (shooter == null) {
                    // Fallback: infer from nearest projectile or shooter entity.
                    shooter = nearestHostileShooter(player);
                }
                if (shooter != null) {
                    shooterDistance = player.distanceTo(shooter);
                    shooterInsideArc = inFront(player, shooter.position(), CONFIG.arcDegrees());
                }
                // Wind-up detected earlier: a ranged mob was drawing within the last few ticks.
                windUpDetected = lastRangedWindUpTick >= 0
                        && (player.tickCount - lastRangedWindUpTick) <= 10;
                if (blocking) arrowsBlocked++; else arrowsNotBlocked++;
            }

            if (blocking) hitsWhileBlocking++; else hitsWhileNotBlocking++;

            if (debug) {
                String hitKind = isArrow ? "ARROW" : "MELEE";
                String msg = String.format(
                        "AutoGuard damage[%s]: blocking=%s using=%s ticksSinceRaise=%s nearestThreat=%.2f ticksSinceAttack=%s",
                        hitKind, blocking, player.isUsingItem(),
                        ticksSinceRaise >= 0 ? Long.toString(ticksSinceRaise) : "not raised",
                        nearestDistance >= 0 ? String.format("%.2f", nearestDistance) : "n/a",
                        ticksSinceAttack >= 0 ? Long.toString(ticksSinceAttack) : "n/a");
                if (isArrow) {
                    msg += String.format(" arrowsBlocked=%d arrowsNotBlocked=%d shooterDist=%.1f shooterInsideArc=%b windUpDetected=%b",
                            arrowsBlocked, arrowsNotBlocked,
                            shooterDistance >= 0 ? shooterDistance : -1.0, shooterInsideArc, windUpDetected);
                }
                feedback(msg);
                LOGGER.info(msg);
            }
        }
        lastHealth = health;
    }

    /** Classify the most recent damage as arrow/ranged-projectile vs melee. */
    private static boolean classifyDamageAsArrow(Player player) {
        try {
            DamageSource source = player.getLastDamageSource();
            if (source != null) {
                Entity direct = source.getEntity();
                if (direct instanceof Projectile) return true;
            }
        } catch (Throwable ignored) {
            // Fall through to projectile inference below.
        }
        // Fallback: if there's a nearby arrow heading toward the player, treat as ranged.
        try {
            var level = CLIENT.level;
            if (level != null) {
                var area = player.getBoundingBox().inflate(6.0);
                for (Entity e : level.getEntities(player, area, candidate -> true)) {
                    if (e instanceof net.minecraft.world.entity.projectile.Projectile) {
                        Vec3 vel = e.getDeltaMovement();
                        Vec3 toPlayer = player.position().subtract(e.position());
                        if (vel.dot(toPlayer) > 0.0) return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return false;
    }

    /** Fallback: find the nearest hostile shooter entity (ranged mob or arrow owner). */
    private static Entity nearestHostileShooter(Player player) {
        try {
            var level = CLIENT.level;
            if (level == null) return null;
            var area = player.getBoundingBox().inflate(24.0);
            Entity nearest = null;
            double nearestDist = -1.0;
            for (Entity e : level.getEntities(player, area, candidate -> true)) {
                if (e == player || e.isRemoved()) continue;
                if (!(e instanceof LivingEntity)) continue;
                Identifier id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
                if (id == null) continue;
                String path = id.getPath();
                boolean isRanged = "skeleton".equals(path) || "stray".equals(path) || "bogged".equals(path)
                        || "pillager".equals(path) || "piglin".equals(path) || "drowned".equals(path);
                if (!isRanged) continue;
                double d = player.distanceTo(e);
                if (nearestDist < 0.0 || d < nearestDist) {
                    nearestDist = d;
                    nearest = e;
                }
            }
            return nearest;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean inFront(Player player, Vec3 threatPosition, double arcDegrees) {
        Vec3 towardThreat = threatPosition.subtract(player.position()).normalize();
        double halfArcCos = Math.cos(Math.toRadians(arcDegrees * 0.5));
        return player.getLookAngle().dot(towardThreat) >= halfArcCos;
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

    private static void releaseIfOwned(Minecraft client, boolean stopVanillaUse, String reason) {
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
            debug("RELEASE " + reason, client.player);
            raiseStartTick = -1L;
        }
        clearOwnership();
    }

    private static void clearOwnership() {
        raisedByMod = false;
        raisedHand = null;
        noThreatSince = -1L;
        retryAfterTick = 0L;
        attackInputUntilTick = 0L;
        maxHoldUntilTick = 0L;
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

    /**
     * Called when the player attacks (entity or block). Release the shield so the attack
     * becomes a real hit, then keep the shield down while attack input is active.
     */
    private static void yieldForAttack() {
        long tick = CLIENT.player == null ? 0L : CLIENT.player.tickCount;
        lastAttackTick = tick;
        // Keep shield down for resumeDelaySeconds after the attack callback ends.
        long resumeDelayTicks = Math.round(CONFIG.resumeDelaySeconds() * 20.0);
        attackInputUntilTick = tick + resumeDelayTicks;
        if (raisedByMod) {
            releaseIfOwned(CLIENT, true, "attack-callback");
            releasesForAttack++;
        }
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
                            .then(literal("stats")
                                    .then(literal("reset").executes(context -> {
                                        resetStats();
                                        feedback("AutoGuard stats reset.");
                                        return 1;
                                    }))
                                    .executes(context -> showStats())
                            )
            );
        });
    }

    private static int showStats() {
        feedback("AutoGuard stats: hitsBlocking=" + hitsWhileBlocking
                + " hitsNotBlocking=" + hitsWhileNotBlocking
                + " arrowsBlocked=" + arrowsBlocked
                + " arrowsNotBlocked=" + arrowsNotBlocked
                + " raises=" + shieldRaises
                + " releasesForAttack=" + releasesForAttack);
        return 1;
    }

    private static void resetStats() {
        hitsWhileBlocking = 0;
        hitsWhileNotBlocking = 0;
        arrowsBlocked = 0;
        arrowsNotBlocked = 0;
        shieldRaises = 0;
        releasesForAttack = 0;
        lastAttackTick = -1L;
        raiseStartTick = -1L;
        lastRangedWindUpTick = -1L;
    }

    private static int setEnabled(boolean value) {
        CONFIG.enabled = value;
        CONFIG.save();
        if (!value) releaseIfOwned(CLIENT, true, "disabled");
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
