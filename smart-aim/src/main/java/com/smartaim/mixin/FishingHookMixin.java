// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.mixin;

import com.smartaim.aim.AimHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
   @Unique
   private boolean smartaim$touchedWater;

   @Inject(method = "tick", at = @At("RETURN"))
   private void smartaim$steerTowardsTarget(CallbackInfo info) {
      FishingHook hook = (FishingHook)this;
      if (!hook.level().isClientSide()) {
         if (this.smartaim$touchedWater || smartaim$inWater(hook)) {
            this.smartaim$touchedWater = true;
            AimHelper.forget(hook.getUUID());
         } else if (hook.getHookedIn() != null) {
            AimHelper.forget(hook.getUUID());
         } else if (!hook.onGround() && hook.isAlive() && hook.getPlayerOwner() instanceof Player) {
            AimHelper.tick((Projectile)this, false);
         }
      }
   }

   @Unique
   private static boolean smartaim$inWater(FishingHook hook) {
      if (hook.isInWater()) {
         return true;
      }

      Level level = hook.level();
      BlockPos pos = hook.blockPosition();
      FluidState fluid = level.getFluidState(pos);
      return fluid.is(FluidTags.WATER) && fluid.getHeight(level, pos) > 0.0F;
   }
}
