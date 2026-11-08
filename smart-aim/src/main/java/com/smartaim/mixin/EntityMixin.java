package com.smartaim.mixin;

import com.smartaim.aim.AimHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class EntityMixin {
   @Inject(method = "remove", at = @At("HEAD"))
   private void smartaim$clearProjectileTracking(Entity.RemovalReason reason, CallbackInfo info) {
      Entity entity = (Entity)(Object)this;
      if (entity instanceof Projectile) {
         UUID id = entity.getUUID();
         AimHelper.forget(id);
      }
   }
}
