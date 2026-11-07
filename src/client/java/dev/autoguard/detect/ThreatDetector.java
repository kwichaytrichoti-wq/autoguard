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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/**
 * Small-radius, client-observable threat heuristics. Never reads a mob's server-side goal target.
 * <p>
 * Speed-aware raising: for melee threats, estimate the ticks until the attacker can hit
 * (distance to its attack reach divided by its approach speed) and flag the entity as a
 * threat when that estimate is at most {@code config.leadTicks()}, in addition to the
 * existing proximity rule. This lets the shield be raised before the attacker is in reach,
 * giving the 5-tick vanilla shield raise delay time to complete.
 */
public final class ThreatDetector {
    private static final double RADIUS = 8.0;
    private ThreatDetector() {}

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
            // Applies to non-monster mobs that are angry (e.g. provoked neutral mobs, iron golems).
            boolean angryNeutral = entity instanceof Mob mob && config.angryNeutralMobs && mob.isAggressive() && !(entity instanceof Monster);
            if (!hostile && !angryNeutral) continue;
            if (!config.melee || !inFront(player, entity.position(), config.arcDegrees())) continue;
            double distance = player.distanceTo(entity);
            // Proximity rule: threat if within early margin of reach.
            if (distance > 3.0 + config.earlyMargin()) {
                // Speed-aware rule: estimate ticks until the mob can reach the player.
                // If the mob will be in reach within leadTicks, treat as a threat now so the
                // shield has time to raise (vanilla needs 5 ticks before blocking activates).
                if (!speedAwareThreat(player, living, distance, config)) continue;
            }
            if (approachingOrFacing(player, living, distance)) return true;
        }
        return false;
    }

    /**
     * Estimate ticks until the mob can hit the player.
     * <p>
     * Uses distance minus the mob's attack reach, divided by the mob's horizontal approach
     * speed. If the result is at most {@code config.leadTicks()}, the mob will be able to hit
     * soon enough that the shield should start raising now.
     */
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
        // 26.3: LivingEntity has no exposed swing field; swing cue dropped.
        // Removed the "distance < 2.0 is always a threat" shortcut: proximity alone is not a reliable threat signal.
        return approaching || lookingAtPlayer;
    }

    /** Distance to the nearest threat entity, or -1 if none. Used by the damage report. */
    static double nearestThreatDistance(Level level, Player player, GuardConfig config) {
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
