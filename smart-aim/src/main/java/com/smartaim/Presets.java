// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim;

public enum Presets {
   MILD("smartaim.preset.mild", 16.0, 6.0, 12.0, false),
   NORMAL("smartaim.preset.normal", 24.0, 10.0, 20.0, false),
   STRONG("smartaim.preset.strong", 32.0, 16.0, 28.0, true);

   private final String translationKey;
   private final double range;
   private final double maxTurnDegrees;
   private final double coneAngle;
   private final boolean trackingEnabled;

   Presets(String translationKey, double range, double maxTurnDegrees, double coneAngle, boolean trackingEnabled) {
      this.translationKey = translationKey;
      this.range = range;
      this.maxTurnDegrees = maxTurnDegrees;
      this.coneAngle = coneAngle;
      this.trackingEnabled = trackingEnabled;
   }

   public String translationKey() {
      return this.translationKey;
   }

   public double range() {
      return this.range;
   }

   public double maxTurnDegrees() {
      return this.maxTurnDegrees;
   }

   public double coneAngle() {
      return this.coneAngle;
   }

   public void applyTo(SmartAimConfig config) {
      config.range = this.range;
      config.maxTurnDegrees = this.maxTurnDegrees;
      config.coneAngle = this.coneAngle;
      config.trackingEnabled = this.trackingEnabled;
   }
}
