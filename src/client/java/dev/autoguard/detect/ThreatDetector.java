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

import java.util.UUID;

/** Small-radius, client-observable threat heuristics. Never reads a mob's server-side goal target. */
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
            if (distance > 3.0 + config.earlyMargin()) continue;
            if (approachingOrFacing(player, living, distance)) return true;
        }
        return false;
    }

    private static boolean isFriendly(Player player, Entity entity) {
        UUID playerUuid = player.getUUID();
        // Team-based alliance: only meaningful when both sides have teams.
        if (entity.getTeam() != null && player.getTeam() != null && entity.isAlliedTo(player)) return true;
        // Player-owned pets (tamed wolves, cats, etc.) via OwnableEntity.getOwnerUUID.
        // isAlliedTo alone is NOT enough: it only works when both entities have teams.
        if (entity instanceof OwnableEntity ownable) {
            UUID ownerUuid = ownable.getOwnerUUID();
            if (ownerUuid != null && ownerUuid.equals(playerUuid)) return true;
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

    private static boolean inFront(Player player, Vec3 threatPosition, double arcDegrees) {
        Vec3 towardThreat = threatPosition.subtract(player.position()).normalize();
        double halfArcCos = Math.cos(Math.toRadians(arcDegrees * 0.5));
        return player.getLookAngle().dot(towardThreat) >= halfArcCos;
    }
}
