// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.smartaim.SmartAimConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartAimKeybinds {
   public static final String KEY_TOGGLE = "key.smartaim.toggle";
   private static final Logger LOGGER = LoggerFactory.getLogger("smartaim/client/keys");
   private static KeyMapping toggleKey;

   private SmartAimKeybinds() {
   }

   public static void register() {
      toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.smartaim.toggle", Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), Category.MISC));
      ClientTickEvents.END_CLIENT_TICK.register(SmartAimKeybinds::tick);
   }

   public static KeyMapping toggleKey() {
      return toggleKey;
   }

   private static void tick(Minecraft minecraft) {
      if (toggleKey != null) {
         while (toggleKey.consumeClick()) {
            toggle(minecraft);
         }
      }
   }

   private static void toggle(Minecraft minecraft) {
      if (!minecraft.hasSingleplayerServer()) {
         LOGGER.debug("Toggle key ignored: the config of a remote server belongs to that server");
      } else {
         SmartAimConfig config = SmartAimConfig.get();
         config.enabled = !config.enabled;
         SmartAimConfig.apply(config);
         Component message = Component.translatable(
            "smartaim.command.toggled", new Object[]{config.enabled ? Component.translatable("smartaim.text.on") : Component.translatable("smartaim.text.off")}
         );
         LocalPlayer player = minecraft.player;
         if (player != null && minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.setOverlayMessage(message, false);
         }
      }
   }
}
