// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim;

import com.smartaim.aim.ProjectileKind;
import com.smartaim.command.SmartAimCommand;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartAim implements ModInitializer {
   public static final String MOD_ID = "smartaim";
   public static final Logger LOGGER = LoggerFactory.getLogger("smartaim");

   public void onInitialize() {
      SmartAimConfig.load();
      SmartAimCommand.register();
      LOGGER.info("Smart Aim ready (projectile flight assist: {} types)", ProjectileKind.values().length);
   }
}
