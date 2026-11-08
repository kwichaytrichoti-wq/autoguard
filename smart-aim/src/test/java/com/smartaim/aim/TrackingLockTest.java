package com.smartaim.aim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrackingLockTest {
   private static final TrackingLock.Limits LIMITS = new TrackingLock.Limits(32.0, 100.0, 15, 100, 6.0);

   private TrackingLock.Conditions conditions() {
      return new TrackingLock.Conditions(true, false, true, true, true, true,
         10.0, 20.0, 0, 1, 0.0, false, false, false, false, false);
   }

   private TrackingLock.ReleaseReason reason(TrackingLock.Conditions c) {
      return TrackingLock.releaseReason(c, LIMITS);
   }

   @Test void validLockHasNoReleaseReason() { assertNull(reason(conditions())); }

   @Test void configuredBoundariesAreInclusive() {
      var c = conditions();
      assertNull(reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(),
         32.0, 100.0, 14, 100, 6.0, false, false, false, false, false)));
   }

   @Test void releasesUnavailableTarget() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.TARGET_UNAVAILABLE, reason(new TrackingLock.Conditions(false, c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }
   @Test void releasesDisallowedTarget() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.TARGET_DISALLOWED, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), false, c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }
   @Test void releasesWhenOccluded() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.NO_LINE_OF_SIGHT, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), false, c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }
   @Test void releasesOutOfRange() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.OUT_OF_RANGE, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), 33.0, c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }
   @Test void releasesTargetBehindProjectile() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.TARGET_BEHIND, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), 101.0, c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }
   @Test void releasesAfterConsecutiveGrowingDistance() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.DISTANCE_GROWING, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), 15, c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }
   @Test void releasesAfterMaximumDuration() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.MAX_DURATION, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), 101, c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }
   @Test void releasesOnTeleport() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.TELEPORTED, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), 6.1, c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }
   @Test void releasesStuckInGroundReturningHookedAndSlowProjectiles() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.STUCK, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), true, false, false, false, false)));
      assertEquals(TrackingLock.ReleaseReason.STUCK, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), false, true, false, false, false)));
      assertEquals(TrackingLock.ReleaseReason.RETURNING, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), false, false, true, false, false)));
      assertEquals(TrackingLock.ReleaseReason.HOOKED, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), false, false, false, true, false)));
      assertEquals(TrackingLock.ReleaseReason.TOO_SLOW, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), false, false, false, false, true)));
   }
   @Test void releasesForDeathRemovalUnloadedOrDimensionChange() {
      var c = conditions();
      assertEquals(TrackingLock.ReleaseReason.TARGET_UNAVAILABLE, reason(new TrackingLock.Conditions(false, c.removed(), c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
      assertEquals(TrackingLock.ReleaseReason.TARGET_UNAVAILABLE, reason(new TrackingLock.Conditions(c.alive(), true, c.loaded(), c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
      assertEquals(TrackingLock.ReleaseReason.TARGET_UNAVAILABLE, reason(new TrackingLock.Conditions(c.alive(), c.removed(), false, c.sameDimension(), c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
      assertEquals(TrackingLock.ReleaseReason.TARGET_UNAVAILABLE, reason(new TrackingLock.Conditions(c.alive(), c.removed(), c.loaded(), false, c.allowed(), c.lineOfSight(), c.distance(), c.angleDegrees(), c.distanceGrowingTicks(), c.trackingTicks(), c.targetStepDistance(), c.stuck(), c.inGround(), c.returning(), c.hooked(), c.tooSlow())));
   }

   @Test void removedProjectileClearsOnlyItsUuidEntry() {
      TrackingLockRegistry registry = new TrackingLockRegistry();
      UUID firstProjectile = UUID.randomUUID();
      UUID secondProjectile = UUID.randomUUID();
      UUID target = UUID.randomUUID();
      TrackingLock lock = new TrackingLock(target, "minecraft:overworld", 0, 0, 0, 4);
      registry.put(firstProjectile, lock);
      registry.put(secondProjectile, lock);
      registry.clear(firstProjectile);
      assertEquals(1, registry.size());
      assertEquals(lock, registry.get(secondProjectile));
   }

   @Test void storesOnlyTheTargetIdentityAndScalarHistory() {
      UUID id = UUID.randomUUID();
      TrackingLock lock = new TrackingLock(id, "minecraft:overworld", 1, 2, 3, 10);
      lock.observe(1, 2, 3, 11);
      lock.observe(1, 2, 3, 12);
      assertEquals(id, lock.targetId());
      assertEquals(3, lock.projectedGrowingDistanceTicks(13));
      assertEquals(3, lock.trackingTicks());
   }
}
