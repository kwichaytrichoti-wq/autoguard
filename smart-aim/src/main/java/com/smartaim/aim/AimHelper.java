// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.aim;

import com.smartaim.SmartAimConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class AimHelper {
   private static final double MIN_SPEED = 1.0E-4;
   private static final double MIN_DISTANCE = 1.0E-4;
   private static final double MAX_SPEED_SCALE = 3.0;
   private static final double HARD_TURN_CAP_DEGREES = 45.0;
   private static final double MAX_LEAD_BLOCKS = 8.0;
   private static final TrackingLockRegistry TRACKING_LOCKS = new TrackingLockRegistry();

   private AimHelper() {
   }

   public static void tick(Projectile projectile, boolean stuck) {
      Level level = projectile.level();
      if (level.isClientSide()) return;
      UUID projectileId = projectile.getUUID();
      if (stuck || !projectile.isAlive()) {
         forget(projectileId);
         return;
      }

      Entity owner = projectile.getOwner();
      if (!(owner instanceof Player)) {
         forget(projectileId);
         return;
      }
      SmartAimConfig config = SmartAimConfig.get();
      if (!config.enabled) {
         forget(projectileId);
         return;
      }
      ProjectileKind kind = ProjectileKind.of(projectile);
      if (kind == null || !config.isProjectileEnabled(kind) || projectile.noPhysics) {
         forget(projectileId);
         return;
      }

      Vec3 velocity = projectile.getDeltaMovement();
      double speed = velocity.length();
      if (speed < MIN_SPEED) {
         forget(projectileId);
         return;
      }

      Vec3 direction = velocity.scale(1.0 / speed);
      Vec3 origin = projectile.position();
      if (!config.trackingEnabled) forget(projectileId);
      TrackingLock lock = config.trackingEnabled ? TRACKING_LOCKS.get(projectileId) : null;
      AimTarget target = null;

      // Resolve a lock by UUID in this server level. An absent entity is either unloaded,
      // removed, or in another dimension; all three invalidate the lock without retaining it.
      if (lock != null) {
         LivingEntity lockedTarget = level instanceof ServerLevel serverLevel
            ? (LivingEntity)serverLevel.getEntity(lock.targetId())
            : null;
         if (lockedTarget != null && !releaseLockedTarget(lock, lockedTarget, level, projectile, owner,
               direction, origin, speed, stuck, config)) {
            Vec3 point = lockedTarget.getBoundingBox().getCenter();
            if (config.leadTarget) point = leadPoint(lockedTarget, point, origin, speed, kind.drag());
            target = new AimTarget(lockedTarget, point);
            double distance = pointDistance(origin, lockedTarget.getBoundingBox().getCenter());
            Vec3 centre = lockedTarget.getBoundingBox().getCenter();
            lock.observe(centre.x, centre.y, centre.z, distance);
         } else {
            forget(projectileId);
            lock = null;
         }
      }

      // The legacy cone scan is deliberately unchanged and runs only for an unlocked shot.
      if (target == null) {
         target = findBestTarget(level, projectile, owner, origin, direction, speed, kind.drag(), config);
         if (target != null && config.trackingEnabled) {
            Vec3 centre = target.mob().getBoundingBox().getCenter();
            TRACKING_LOCKS.put(projectileId, new TrackingLock(target.mob().getUUID(), level.dimension().toString(),
               centre.x, centre.y, centre.z, pointDistance(origin, centre)));
         }
      }

      if (target == null) return;
      Vec3 toAimPoint = target.aimPoint().subtract(origin);
      if (toAimPoint.lengthSqr() < 1.0E-8) return;
      double turnBudget = AimMath.turnBudgetDegrees(config.maxTurnDegrees, speed, kind.launchSpeed(), 3.0, HARD_TURN_CAP_DEGREES);
      if (turnBudget < 1.0E-6) return;
      double[] desired = kind.usesDrag()
         ? AimMath.ballisticAimWithDrag(
            new double[]{origin.x, origin.y, origin.z},
            new double[]{target.aimPoint().x, target.aimPoint().y, target.aimPoint().z},
            speed, kind.gravity(), kind.drag())
         : AimMath.ballisticAim(
            new double[]{origin.x, origin.y, origin.z},
            new double[]{target.aimPoint().x, target.aimPoint().y, target.aimPoint().z},
            speed, kind.gravity());
      if (desired == null) desired = new double[]{toAimPoint.x, toAimPoint.y, toAimPoint.z};
      double[] rotated = AimMath.rotateTowards(new double[]{direction.x, direction.y, direction.z}, desired, turnBudget);
      if (rotated != null) {
         projectile.setDeltaMovement(new Vec3(rotated[0], rotated[1], rotated[2]).scale(speed));
         projectile.syncVelocity = true;
         if (config.debugParticles && level instanceof ServerLevel serverLevel) {
            Vec3 marker = target.mob().getBoundingBox().getCenter();
            serverLevel.sendParticles(ParticleTypes.CRIT, marker.x, marker.y, marker.z, 2, 0.15, 0.15, 0.15, 0.0);
         }
      }
   }

   public static void forget(UUID projectileId) {
      if (projectileId != null) TRACKING_LOCKS.clear(projectileId);
   }

   static int activeLockCount() { return TRACKING_LOCKS.size(); }

   private static boolean releaseLockedTarget(TrackingLock lock, LivingEntity target, Level level,
                                               Projectile projectile, Entity owner, Vec3 direction,
                                               Vec3 origin, double speed, boolean stuck,
                                               SmartAimConfig config) {
      Vec3 centre = target.getBoundingBox().getCenter();
      Vec3 toTarget = centre.subtract(origin);
      double distance = toTarget.length();
      double angle = distance < MIN_DISTANCE ? 0.0 : AimMath.angleDegrees(
         new double[]{direction.x, direction.y, direction.z},
         new double[]{toTarget.x, toTarget.y, toTarget.z});
      double moved = lock.targetStepDistance(centre.x, centre.y, centre.z);
      boolean hooked = projectile instanceof FishingHook fishingHook && fishingHook.getHookedIn() != null;
      boolean returning = projectile instanceof ThrownTrident && headingBackToOwner(projectile, owner);
      boolean inGround = stuck || projectile.onGround();
      TrackingLock.Conditions conditions = new TrackingLock.Conditions(
         target.isAlive(), target.isRemoved(), true,
         lock.dimensionId().equals(level.dimension().toString()),
         TargetRules.allows(target, owner, config),
         hasLineOfSight(level, projectile, origin, centre),
         distance, angle,
         lock.projectedGrowingDistanceTicks(distance), lock.trackingTicks() + 1, moved,
         stuck, inGround, returning, hooked, speed < MIN_SPEED);
      TrackingLock.Limits limits = new TrackingLock.Limits(config.trackingRange, config.trackingMaxAngle,
         config.trackingGiveUpTicks, config.trackingMaxTicks, config.trackingTeleportDistance);
      return TrackingLock.releaseReason(conditions, limits) != null;
   }

   private static boolean headingBackToOwner(Projectile projectile, Entity owner) {
      Vec3 toOwner = owner.position().subtract(projectile.position());
      double distance = toOwner.length();
      if (distance < 1.0e-4 || distance > 24.0) return false;
      Vec3 velocity = projectile.getDeltaMovement();
      double speed = velocity.length();
      return speed > MIN_SPEED && velocity.scale(1.0 / speed).dot(toOwner.scale(1.0 / distance)) > 0.65;
   }

   private static Vec3 leadPoint(LivingEntity target, Vec3 centre, Vec3 origin, double speed, double drag) {
      double distance = pointDistance(origin, centre);
      double ticks = AimMath.ticksToImpact(distance, speed, drag);
      Vec3 velocity = target.getDeltaMovement();
      double maxLead = Math.min(MAX_LEAD_BLOCKS, distance * 0.5);
      double[] lead = AimMath.leadPoint(new double[]{centre.x, centre.y, centre.z},
         new double[]{velocity.x, velocity.y, velocity.z}, ticks, maxLead);
      return new Vec3(lead[0], lead[1], lead[2]);
   }

   private static double pointDistance(Vec3 from, Vec3 to) { return from.distanceTo(to); }

   private static AimHelper.AimTarget findBestTarget(
      Level level, Projectile projectile, Entity shooter, Vec3 origin, Vec3 direction, double speed, double drag, SmartAimConfig config
   ) {
      double range = config.range;
      Vec3 ahead = origin.add(direction.scale(range));
      double lateral = Math.max(1.0, range * Math.tan(Math.toRadians(config.coneAngle)));
      AABB searchBox = new AABB(
         Math.min(origin.x, ahead.x) - lateral,
         Math.min(origin.y, ahead.y) - lateral,
         Math.min(origin.z, ahead.z) - lateral,
         Math.max(origin.x, ahead.x) + lateral,
         Math.max(origin.y, ahead.y) + lateral,
         Math.max(origin.z, ahead.z) + lateral
      );
      List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> TargetRules.allows(entity, shooter, config));
      if (candidates.isEmpty()) {
         return null;
      }

      double cosCone = Math.cos(Math.toRadians(config.coneAngle));
      double[] originArray = new double[]{origin.x, origin.y, origin.z};
      double[] dirArray = new double[]{direction.x, direction.y, direction.z};
      LivingEntity bestMob = null;
      Vec3 bestAimPoint = null;
      double bestLineDistance = Double.MAX_VALUE;
      double bestDistance = Double.MAX_VALUE;

      for (LivingEntity mob : candidates) {
         Vec3 centre = mob.getBoundingBox().getCenter();
         Vec3 toMob = centre.subtract(origin);
         double distance = toMob.length();
         if (!(distance < 1.0E-4) && !(distance > range) && !(toMob.scale(1.0 / distance).dot(direction) < cosCone)) {
            Vec3 aimPoint = centre;
            if (config.leadTarget) {
               double ticks = AimMath.ticksToImpact(distance, speed, drag);
               Vec3 mobVelocity = mob.getDeltaMovement();
               double maxLead = Math.min(8.0, distance * 0.5);
               double[] lead = AimMath.leadPoint(
                  new double[]{centre.x, centre.y, centre.z}, new double[]{mobVelocity.x, mobVelocity.y, mobVelocity.z}, ticks, maxLead
               );
               aimPoint = new Vec3(lead[0], lead[1], lead[2]);
            }

            if (hasLineOfSight(level, projectile, origin, aimPoint)) {
               double lineDistance = AimMath.distanceToLine(originArray, dirArray, new double[]{aimPoint.x, aimPoint.y, aimPoint.z});
               if (lineDistance < bestLineDistance - 1.0E-6 || Math.abs(lineDistance - bestLineDistance) <= 1.0E-6 && distance < bestDistance) {
                  bestLineDistance = lineDistance;
                  bestDistance = distance;
                  bestMob = mob;
                  bestAimPoint = aimPoint;
               }
            }
         }
      }

      return bestMob == null ? null : new AimHelper.AimTarget(bestMob, bestAimPoint);
   }

   private static boolean hasLineOfSight(Level level, Projectile projectile, Vec3 from, Vec3 to) {
      return level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, projectile)).getType() == Type.MISS;
   }

   private record AimTarget(LivingEntity mob, Vec3 aimPoint) {
   }
}
