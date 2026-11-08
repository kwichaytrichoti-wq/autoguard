// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.mixin;

import com.smartaim.aim.AimHelper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
   @Shadow
   protected abstract boolean isInGround();

   @Inject(method = "tick", at = @At("RETURN"))
   private void smartaim$steerTowardsTarget(CallbackInfo info) {
      Projectile self = (Projectile)this;
      AimHelper.tick(self, this.isInGround());
   }
}
