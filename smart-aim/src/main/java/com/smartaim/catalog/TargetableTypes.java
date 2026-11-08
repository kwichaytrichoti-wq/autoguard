// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.catalog;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

public final class TargetableTypes {
   private TargetableTypes() {
   }

   public static List<EntityCatalog.Candidate> candidates() {
      List<EntityCatalog.Candidate> candidates = new ArrayList();

      for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
         candidates.add(
            new EntityCatalog.Candidate(
               id(type), type.getDescription().getString(), DefaultAttributes.hasSupplier(type), type == EntityTypes.PLAYER, type == EntityTypes.ARMOR_STAND
            )
         );
      }

      return candidates;
   }

   public static List<EntityCatalog.Entry> entries() {
      return EntityCatalog.select(candidates());
   }

   public static String id(EntityType<?> type) {
      Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
      return key == null ? "" : key.toString();
   }
}
