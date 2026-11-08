package com.smartaim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmartAimConfigTest {
   @Test void legacyConfigGetsNewTrackingDefaults() {
      SmartAimConfig config = SmartAimConfig.fromJson("{\"configVersion\":2,\"enabled\":true,\"range\":24.0}");
      assertEquals(3, config.configVersion);
      assertFalse(config.trackingEnabled);
      assertEquals(32.0, config.trackingRange, 0.0001);
      assertEquals(100, config.trackingMaxTicks);
      assertEquals(100.0, config.trackingMaxAngle, 0.0001);
      assertEquals(15, config.trackingGiveUpTicks);
      assertEquals(6.0, config.trackingTeleportDistance, 0.0001);
      assertEquals(24.0, config.range, 0.0001);
   }

   @Test void trackingValuesAreClampedToDocumentedBounds() {
      SmartAimConfig config = SmartAimConfig.fromJson("{\"trackingRange\":900,\"trackingMaxTicks\":900,\"trackingMaxAngle\":190,\"trackingGiveUpTicks\":0,\"trackingTeleportDistance\":-1}");
      assertEquals(64.0, config.trackingRange, 0.0001);
      assertEquals(400, config.trackingMaxTicks);
      assertEquals(170.0, config.trackingMaxAngle, 0.0001);
      assertEquals(1, config.trackingGiveUpTicks);
      assertEquals(0.1, config.trackingTeleportDistance, 0.0001);
      config.trackingEnabled = true;
      assertTrue(config.copy().trackingEnabled);
   }
}
