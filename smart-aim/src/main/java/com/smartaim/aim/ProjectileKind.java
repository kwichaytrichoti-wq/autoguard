// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.aim;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;

public enum ProjectileKind {
   ARROW("arrow", true, 3.0, 0.05),
   SPECTRAL_ARROW("spectral_arrow", true, 3.0, 0.05),
   TRIDENT("trident", true, 2.5, 0.05),
   SNOWBALL("snowball", true, 1.5, 0.03),
   EGG("egg", true, 1.5, 0.03),
   SPLASH_POTION("splash_potion", true, 0.5, 0.05),
   LINGERING_POTION("lingering_potion", true, 0.5, 0.05),
   ENDER_PEARL("ender_pearl", false, 1.5, 0.03),
   EXPERIENCE_BOTTLE("experience_bottle", false, 0.7, 0.07),
   WIND_CHARGE("wind_charge", false, 1.5, 0.0),
   FIREWORK_ROCKET("firework_rocket", false, 0.5, 0.0),
   FISHING_BOBBER("fishing_bobber", false, 1.1, 0.03, 0.92);

   private final String id;
   private final boolean enabledByDefault;
   private final double launchSpeed;
   private final double gravity;
   private final double drag;

   ProjectileKind(String id, boolean enabledByDefault, double launchSpeed, double gravity) {
      this(id, enabledByDefault, launchSpeed, gravity, 1.0);
   }

   ProjectileKind(String id, boolean enabledByDefault, double launchSpeed, double gravity, double drag) {
      this.id = id;
      this.enabledByDefault = enabledByDefault;
      this.launchSpeed = launchSpeed;
      this.gravity = gravity;
      this.drag = drag;
   }

   public String id() {
      return this.id;
   }

   public boolean enabledByDefault() {
      return this.enabledByDefault;
   }

   public double launchSpeed() {
      return this.launchSpeed;
   }

   public double gravity() {
      return this.gravity;
   }

   public double drag() {
      return this.drag;
   }

   public boolean usesDrag() {
      return this.drag < 1.0;
   }

   public static ProjectileKind of(Projectile projectile) {
      if (projectile instanceof ThrownTrident) {
         return TRIDENT;
      } else if (projectile instanceof SpectralArrow) {
         return SPECTRAL_ARROW;
      } else if (projectile instanceof Arrow) {
         return ARROW;
      } else if (projectile instanceof Snowball) {
         return SNOWBALL;
      } else if (projectile instanceof ThrownEgg) {
         return EGG;
      } else if (projectile instanceof ThrownSplashPotion) {
         return SPLASH_POTION;
      } else if (projectile instanceof ThrownLingeringPotion) {
         return LINGERING_POTION;
      } else if (projectile instanceof ThrownEnderpearl) {
         return ENDER_PEARL;
      } else if (projectile instanceof ThrownExperienceBottle) {
         return EXPERIENCE_BOTTLE;
      } else if (projectile instanceof WindCharge) {
         return WIND_CHARGE;
      } else if (projectile instanceof FireworkRocketEntity) {
         return FIREWORK_ROCKET;
      } else {
         return projectile instanceof FishingHook ? FISHING_BOBBER : null;
      }
   }

   public static Map<String, Boolean> defaults() {
      Map<String, Boolean> map = new LinkedHashMap();

      for (ProjectileKind kind : values()) {
         map.put(kind.id, kind.enabledByDefault);
      }

      return map;
   }

   public static ProjectileKind byId(String id) {
      for (ProjectileKind kind : values()) {
         if (kind.id.equals(id)) {
            return kind;
         }
      }

      return null;
   }
}
