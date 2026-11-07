package dev.autoguard.detect;

import dev.autoguard.config.GuardConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Small-radius, client-observable threat heuristics. Never reads a mob's server-side goal target.
 * <p>
 * Speed-aware raising: for melee threats, estimate the ticks until the attacker can hit
 * (distance to its attack reach divided by its approach speed) and flag the entity as a
 * threat when that estimate is at most {@code config.leadTicks()}, in addition to the
 * existing proximity rule. A hard cap ({@code config.maxReactionDistance()}) prevents any
 * melee threat beyond that distance from counting, whatever the lead-ticks estimate says.
 * <p>
 * Ranged wind-up detection: when a ranged mob (skeleton, stray, bogged, pillager, piglin
 * with crossbow, drowned with trident) is drawing its weapon, inside the front arc, looking
 * toward the player, and within {@code config.rangedReactionDistance()}, raise the shield
 * when the mob has been drawing for at least (full draw ticks minus lead ticks minus the
 * 5-tick vanilla shield raise delay). In-flight projectile detection remains as a second
 * layer, and the shield stays up while a projectile is in flight plus the hold-after-threat-gone time.
 */
public final class ThreatDetector {
    private static final double RADIUS = 8.0;

    // Vanilla 26.3 values, confirmed from multiple sources:
    // Bow full draw = 1 s = 20 ticks; Crossbow load = 1.25 s = 25 ticks; Trident charge = 0.5 s = 10 ticks.
    private static final int FULL_DRAW_TICKS_BOW = 20;
    private static final int FULL_DRAW_TICKS_CROSSBOW = 25;
    private static final int FULL_DRAW_TICKS_TRIDENT = 10;
    // Vanilla shield raise delay: 5 ticks before blocking activates.
    private static final int SHIELD_RAISE_DELAY_TICKS = 5;
    // Look-vector threshold: mob must be looking roughly toward the player (dot > 0.5 ≈ 60 degrees).
    private static final double LOOK_AT_PLAYER_THRESHOLD = 0.5;

    private ThreatDetector() {}

    /** Result of a ranged wind-up check. */
    public static final class RangedWindUp {
        public final LivingEntity shooter;
        public final double distance;
        public final boolean insideArc;
        public final boolean lookingAtPlayer;
        public final int ticksDrawing;
        public final int fullDrawTicks;
        public final boolean shouldRaise;

        private RangedWindUp(LivingEntity shooter, double distance, boolean insideArc,
                             boolean lookingAtPlayer, int ticksDrawing, int fullDrawTicks, boolean shouldRaise) {
            this.shooter = shooter;
            this.distance = distance;
            this.insideArc = insideArc;
            this.lookingAtPlayer = lookingAtPlayer;
            this.ticksDrawing = ticksDrawing;
            this.fullDrawTicks = fullDrawTicks;
            this.shouldRaise = shouldRaise;
        }
    }

    public static boolean hasThreat(Level level, Player player, GuardConfig config) {
        AABB area = player.getBoundingBox().inflate(RADIUS);
        // The bounded query intentionally avoids traversing the full client entity list.
        for (Entity entity : level.getEntities(player, area, candidate -> true)) {
            if (entity == player || entity.isRemoved()) continue;
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id == null || config.neverReactTo(id)) continue;
            if (isFriendly(player, entity)) continue;
            if (config.alwaysReactTo(id)) {
                if (inFront(player, entity.position(), config.arcDegrees())) return true;
                continue;
            }
            if (entity instanceof Projectile projectile) {
                if (config.projectiles && incomingProjectile(player, projectile, config)) return true;
                continue;
            }
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (entity instanceof Creeper creeper) {
                if (config.creepers && isFrontCreeperThreat(player, creeper, config)) return true;
                continue;
            }
            boolean hostile = entity instanceof Monster && config.hostileMobs;
            // 26.3: Mob.isAggressive is client-visible (synced via entity data flags) and needs no ServerLevel.
            boolean angryNeutral = entity instanceof Mob mob && config.angryNeutralMobs && mob.isAggressive() && !(entity instanceof Monster);
            if (!hostile && !angryNeutral) continue;

            double distance = player.distanceTo(entity);

            // Ranged wind-up detection: mob drawing a bow/crossbow/trident.
            if (config.projectiles && isRangedMob(living)) {
                RangedWindUp windUp = checkRangedWindUp(player, living, distance, config);
                if (windUp != null && windUp.shouldRaise) return true;
                // Fall through: even if not raising yet, a non-drawing ranged mob is not a melee threat.
                continue;
            }

            if (!config.melee) continue;
            if (!inFront(player, entity.position(), config.arcDegrees())) continue;

            // HARD CAP: no melee threat counts beyond maxReactionDistance, whatever lead ticks says.
            if (distance > config.maxReactionDistance()) continue;

            // Proximity rule: threat if within early margin of reach.
            if (distance > 3.0 + config.earlyMargin()) {
                // Speed-aware rule: estimate ticks until the mob can reach the player.
                if (!speedAwareThreat(player, living, distance, config)) continue;
            }
            if (approachingOrFacing(player, living, distance)) return true;
        }
        return false;
    }

    /**
     * Check whether a ranged mob is drawing its weapon and should trigger a shield raise.
     * Returns null if the entity is not a ranged mob or not drawing.
     */
    private static RangedWindUp checkRangedWindUp(Player player, LivingEntity mob, double distance, GuardConfig config) {
        if (!isRangedMobDrawing(mob)) return null;

        boolean insideArc = inFront(player, mob.position(), config.arcDegrees());
        if (!insideArc) {
            // Debug-relevant: ranged attacker aiming from outside the arc is logged by the caller.
            return null;
        }

        boolean lookingAtPlayer = isLookingAtPlayer(mob, player);
        if (!lookingAtPlayer) return null;

        if (distance > config.rangedReactionDistance()) return null;

        int fullDrawTicks = fullDrawTicks(mob);
        int ticksDrawing = mob.getTicksUsingItem();
        // Raise when drawing for at least (fullDraw - leadTicks - shieldRaiseDelay),
        // so the shield is fully up by the time the shot leaves.
        int raiseAtTick = fullDrawTicks - (int) Math.round(config.leadTicks()) - SHIELD_RAISE_DELAY_TICKS;
        boolean shouldRaise = ticksDrawing >= raiseAtTick;

        return new RangedWindUp(mob, distance, insideArc, lookingAtPlayer,
                ticksDrawing, fullDrawTicks, shouldRaise);
    }

    /**
     * Get ranged wind-up info for the closest relevant shooter, used by the damage report.
     * Returns null if no ranged mob is currently drawing within range.
     */
    public static RangedWindUp getRangedWindUp(Level level, Player player, GuardConfig config) {
        AABB area = player.getBoundingBox().inflate(RADIUS);
        RangedWindUp best = null;
        for (Entity entity : level.getEntities(player, area, candidate -> true)) {
            if (entity == player || entity.isRemoved()) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!isRangedMob(living)) continue;
            if (isFriendly(player, entity)) continue;
            double distance = player.distanceTo(entity);
            RangedWindUp wu = checkRangedWindUp(player, living, distance, config);
            if (wu == null) continue;
            if (best == null || wu.distance < best.distance) best = wu;
        }
        return best;
    }

    private static boolean isRangedMob(LivingEntity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null) return false;
        String path = id.getPath();
        return "skeleton".equals(path) || "stray".equals(path) || "bogged".equals(path)
                || "pillager".equals(path) || "piglin".equals(path) || "drowned".equals(path);
    }

    private static boolean isRangedMobDrawing(LivingEntity mob) {
        if (!mob.isUsingItem()) return false;
        ItemStack item = mob.getUseItem();
        return item.is(Items.BOW) || item.is(Items.CROSSBOW) || item.is(Items.TRIDENT);
    }

    private static int fullDrawTicks(LivingEntity mob) {
        ItemStack item = mob.getUseItem();
        if (item.is(Items.CROSSBOW)) return FULL_DRAW_TICKS_CROSSBOW;
        if (item.is(Items.TRIDENT)) return FULL_DRAW_TICKS_TRIDENT;
        return FULL_DRAW_TICKS_BOW; // bow and anything else
    }

    private static boolean isLookingAtPlayer(LivingEntity mob, Player player) {
        Vec3 towardPlayer = player.position().subtract(mob.position()).normalize();
        return mob.getLookAngle().dot(towardPlayer) > LOOK_AT_PLAYER_THRESHOLD;
    }

    /** Estimate ticks until the mob can hit the player. */
    private static boolean speedAwareThreat(Player player, LivingEntity mob, double distance, GuardConfig config) {
        double attackReach = mobAttackReach(mob);
        double remaining = distance - attackReach;
        if (remaining <= 0.0) return true; // already in reach
        Vec3 motion = mob.getDeltaMovement();
        double speedSq = motion.horizontalDistanceSqr();
        if (speedSq <= 0.0004) return false; // not moving toward player
        double speed = Math.sqrt(speedSq);
        double ticksUntilHit = remaining / speed;
        return ticksUntilHit <= config.leadTicks();
    }

    /** Typical melee attack reach for a mob (blocks). Most mobs use 3.0. */
    private static double mobAttackReach(LivingEntity mob) {
        // Most hostile mobs have an attack reach of 3.0 blocks.
        return 3.0;
    }

    private static boolean isFriendly(Player player, Entity entity) {
        // Team-based alliance: only meaningful when both sides have teams.
        if (entity.getTeam() != null && player.getTeam() != null && entity.isAlliedTo(player)) return true;
        // Player-owned pets (tamed wolves, cats, etc.) via OwnableEntity.getOwner().
        // isAlliedTo alone is NOT enough: it only works when both entities have teams.
        if (entity instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner != null && owner.equals(player)) return true;
        }
        return false;
    }

    private static boolean incomingProjectile(Player player, Projectile projectile, GuardConfig config) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speedSq = velocity.lengthSqr();
        if (speedSq < 1.0e-5) return false;
        Entity owner = projectile.getOwner();
        if (owner != null && owner.isAlliedTo(player)) return false;
        Vec3 toPlayer = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0)
                .subtract(projectile.position());
        double distance = toPlayer.length();
        double speed = Math.sqrt(speedSq);
        double ticks = distance / speed;
        if (ticks > config.projectileArrivalTicks() || ticks < 0.0) return false;
        if (velocity.dot(toPlayer) <= 0.0) return false;
        Vec3 predicted = projectile.position().add(velocity.scale(ticks));
        if (predicted.distanceToSqr(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0)) > 1.8 * 1.8) return false;
        return inFront(player, projectile.position(), config.arcDegrees());
    }

    private static boolean isFrontCreeperThreat(Player player, Creeper creeper, GuardConfig config) {
        if (!inFront(player, creeper.position(), config.arcDegrees())) return false;
        double distance = player.distanceTo(creeper);
        // 26.3: Creeper.getSwell(float) is gone; no client-visible fuse stage remains in the mappings.
        // Rely on proximity plus approach/facing instead. Creeper reaction is off by default.
        return distance < 3.0 + config.earlyMargin() && approachingOrFacing(player, creeper, distance);
    }

    private static boolean approachingOrFacing(Player player, LivingEntity mob, double distance) {
        Vec3 towardPlayer = player.position().subtract(mob.position()).normalize();
        Vec3 motion = mob.getDeltaMovement();
        boolean approaching = motion.horizontalDistanceSqr() > 0.0004 && motion.normalize().dot(towardPlayer) > 0.18;
        boolean lookingAtPlayer = mob.getLookAngle().dot(towardPlayer) > 0.55;
        return approaching || lookingAtPlayer;
    }

    /** Distance to the nearest threat entity, or -1 if none. Used by the damage report. */
    public static double nearestThreatDistance(Level level, Player player, GuardConfig config) {
        AABB area = player.getBoundingBox().inflate(RADIUS);
        double nearest = -1.0;
        for (Entity entity : level.getEntities(player, area, candidate -> true)) {
            if (entity == player || entity.isRemoved()) continue;
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id == null || config.neverReactTo(id)) continue;
            if (isFriendly(player, entity)) continue;
            if (entity instanceof Projectile) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (entity instanceof Creeper && !config.creepers) continue;
            boolean hostile = entity instanceof Monster && config.hostileMobs;
            boolean angryNeutral = entity instanceof Mob mob && config.angryNeutralMobs && mob.isAggressive() && !(entity instanceof Monster);
            if (!hostile && !angryNeutral) continue;
            if (!config.melee || !inFront(player, entity.position(), config.arcDegrees())) continue;
            double distance = player.distanceTo(entity);
            // HARD CAP: same as hasThreat.
            if (distance > config.maxReactionDistance()) continue;
            if (nearest < 0.0 || distance < nearest) nearest = distance;
        }
        return nearest;
    }

    private static boolean inFront(Player player, Vec3 threatPosition, double arcDegrees) {
        Vec3 towardThreat = threatPosition.subtract(player.position()).normalize();
        double halfArcCos = Math.cos(Math.toRadians(arcDegrees * 0.5));
        return player.getLookAngle().dot(towardThreat) >= halfArcCos;
    }
}
