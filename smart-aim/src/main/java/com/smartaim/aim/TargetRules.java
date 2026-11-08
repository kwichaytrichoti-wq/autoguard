// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.aim;

import com.smartaim.SmartAimConfig;
import java.util.Locale;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

public final class TargetRules {
   public static final String ALLOW = "allow";
   public static final String DENY = "deny";
   public static final String[] ALL_MOBS_DENIED = new String[]{"minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem"};

   private TargetRules() {
   }

   public static boolean isAlwaysExcluded(Entity candidate, Entity shooter) {
      if (candidate == null || candidate == shooter || !candidate.isAlive() || candidate.isRemoved()) {
         return true;
      } else if (candidate.isSpectator()) {
         return true;
      } else {
         return shooter != null && isSameRide(candidate, shooter) ? true : !(candidate instanceof Mob);
      }
   }

   public static boolean isSameRide(Entity candidate, Entity shooter) {
      for (Entity vehicle = shooter.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) {
         if (vehicle == candidate) {
            return true;
         }
      }

      Entity rootVehicle = shooter.getRootVehicle();
      if (rootVehicle == candidate) {
         return true;
      }

      for (Entity passenger : shooter.getPassengers()) {
         if (passenger == candidate) {
            return true;
         }
      }

      if (rootVehicle != shooter) {
         for (Entity passenger : rootVehicle.getPassengers()) {
            if (passenger == candidate) {
               return true;
            }
         }
      }

      return false;
   }

   public static TargetRules.Category categoryOfTraits(boolean friendlyType, boolean neutral, boolean hostile) {
      if (friendlyType) {
         return TargetRules.Category.FRIENDLY;
      } else if (neutral) {
         return TargetRules.Category.NEUTRAL;
      } else {
         return hostile ? TargetRules.Category.HOSTILE : TargetRules.Category.FRIENDLY;
      }
   }

   public static TargetRules.Category categoryOf(Mob mob) {
      return categoryOfTraits(isFriendlyType(mob), mob instanceof NeutralMob, isHostile(mob));
   }

   public static boolean isFriendlyType(Mob mob) {
      return mob instanceof AbstractVillager || mob instanceof AbstractGolem;
   }

   public static boolean isHostile(Mob mob) {
      return mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER;
   }

   public static boolean isTamedPet(Mob mob) {
      if (mob instanceof TamableAnimal tamable && tamable.isTame()) {
         return true;
      } else {
         return mob instanceof AbstractHorse horse && horse.isTamed() ? true : mob instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player;
      }
   }

   public static boolean allows(Entity candidate, Entity shooter, SmartAimConfig config) {
      if (isAlwaysExcluded(candidate, shooter)) {
         return false;
      }

      String override = config.overrideFor(candidate.getType());
      if ("deny".equals(override)) {
         return false;
      }

      if ("allow".equals(override)) {
         return true;
      }

      Mob mob = (Mob)candidate;
      if (isTamedPet(mob)) {
         return config.categories.tamedPets;
      }

      return switch (categoryOf(mob)) {
         case HOSTILE -> config.categories.hostile;
         case NEUTRAL -> config.categories.neutral;
         case FRIENDLY -> config.categories.friendly;
      };
   }

   public static String normaliseAction(String raw) {
      if (raw == null) {
         return null;
      }

      String value = raw.trim().toLowerCase(Locale.ROOT);
      return !"allow".equals(value) && !"deny".equals(value) ? null : value;
   }

   public static String normaliseEntityId(String raw) {
      if (raw == null) {
         return null;
      }

      String value = raw.trim().toLowerCase(Locale.ROOT);
      return value.isEmpty() ? null : value;
   }

   public static Player ownerOf(Entity entity) {
      return entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player player ? player : null;
   }

   public static String describe(Entity entity) {
      if (!(entity instanceof LivingEntity)) {
         return "not-living";
      } else {
         return entity instanceof Mob mob ? categoryOf(mob).name().toLowerCase(Locale.ROOT) + (isTamedPet(mob) ? "+tamed" : "") : "not-mob";
      }
   }

   public enum Category {
      HOSTILE,
      NEUTRAL,
      FRIENDLY;
   }
}
