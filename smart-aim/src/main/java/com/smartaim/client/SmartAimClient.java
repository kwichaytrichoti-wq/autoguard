// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartAimClient implements ClientModInitializer {
   private static final Logger LOGGER = LoggerFactory.getLogger("smartaim/client");

   public void onInitializeClient() {
      SmartAimKeybinds.register();
      LOGGER.info("Smart Aim client ready (Mod Menu screen, toggle key: {})", "key.smartaim.toggle");
   }
}
