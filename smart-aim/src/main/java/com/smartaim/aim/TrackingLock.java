package com.smartaim.aim;

import java.util.Objects;
import java.util.UUID;

/**
 * Entity-free state for one projectile lock. Deliberately stores only identifiers and
 * scalar snapshots so a lock cannot keep a removed world entity alive.
 */
public final class TrackingLock {
   private final UUID targetId;
   private final String dimensionId;
   private double lastTargetX;
   private double lastTargetY;
   private double lastTargetZ;
   private double lastDistance;
   private int trackingTicks;
   private int growingDistanceTicks;

   public TrackingLock(UUID targetId, String dimensionId, double x, double y, double z, double distance) {
      this.targetId = Objects.requireNonNull(targetId, "targetId");
      this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
      this.lastTargetX = x;
      this.lastTargetY = y;
      this.lastTargetZ = z;
      this.lastDistance = distance;
      this.trackingTicks = 1;
   }

   public UUID targetId() { return this.targetId; }
   public String dimensionId() { return this.dimensionId; }
   public int trackingTicks() { return this.trackingTicks; }
   public int growingDistanceTicks() { return this.growingDistanceTicks; }

   public int projectedGrowingDistanceTicks(double distance) {
      return distance > this.lastDistance + 1.0e-4 ? this.growingDistanceTicks + 1 : 0;
   }

   public double targetStepDistance(double x, double y, double z) {
      double dx = x - this.lastTargetX;
      double dy = y - this.lastTargetY;
      double dz = z - this.lastTargetZ;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   public void observe(double x, double y, double z, double distance) {
      this.trackingTicks++;
      this.growingDistanceTicks = distance > this.lastDistance + 1.0e-4 ? this.growingDistanceTicks + 1 : 0;
      this.lastTargetX = x;
      this.lastTargetY = y;
      this.lastTargetZ = z;
      this.lastDistance = distance;
   }

   public static ReleaseReason releaseReason(Conditions c, Limits l) {
      if (!c.alive || c.removed || !c.loaded || !c.sameDimension) return ReleaseReason.TARGET_UNAVAILABLE;
      if (!c.allowed) return ReleaseReason.TARGET_DISALLOWED;
      if (!c.lineOfSight) return ReleaseReason.NO_LINE_OF_SIGHT;
      if (c.distance > l.range) return ReleaseReason.OUT_OF_RANGE;
      if (c.angleDegrees > l.maxAngle) return ReleaseReason.TARGET_BEHIND;
      if (c.distanceGrowingTicks >= l.giveUpTicks) return ReleaseReason.DISTANCE_GROWING;
      if (c.trackingTicks > l.maxTicks) return ReleaseReason.MAX_DURATION;
      if (c.targetStepDistance > l.teleportDistance) return ReleaseReason.TELEPORTED;
      if (c.stuck || c.inGround) return ReleaseReason.STUCK;
      if (c.returning) return ReleaseReason.RETURNING;
      if (c.hooked) return ReleaseReason.HOOKED;
      if (c.tooSlow) return ReleaseReason.TOO_SLOW;
      return null;
   }

   public enum ReleaseReason {
      TARGET_UNAVAILABLE,
      TARGET_DISALLOWED,
      NO_LINE_OF_SIGHT,
      OUT_OF_RANGE,
      TARGET_BEHIND,
      DISTANCE_GROWING,
      MAX_DURATION,
      TELEPORTED,
      STUCK,
      RETURNING,
      HOOKED,
      TOO_SLOW
   }

   public record Conditions(boolean alive, boolean removed, boolean loaded, boolean sameDimension,
                            boolean allowed, boolean lineOfSight, double distance, double angleDegrees,
                            int distanceGrowingTicks, int trackingTicks, double targetStepDistance,
                            boolean stuck, boolean inGround, boolean returning, boolean hooked, boolean tooSlow) { }

   public record Limits(double range, double maxAngle, int giveUpTicks, int maxTicks, double teleportDistance) { }
}
