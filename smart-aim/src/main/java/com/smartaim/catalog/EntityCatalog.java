// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class EntityCatalog {
   public static final String ALWAYS = "allow";
   public static final String NEVER = "deny";
   public static final String KEY_DEFAULT = "smartaim.state.default";
   public static final String KEY_ALWAYS = "smartaim.state.always";
   public static final String KEY_NEVER = "smartaim.state.never";

   private EntityCatalog() {
   }

   public static List<EntityCatalog.Entry> select(Collection<EntityCatalog.Candidate> candidates) {
      List<EntityCatalog.Entry> entries = new ArrayList();

      for (EntityCatalog.Candidate candidate : candidates) {
         if (candidate.livingMob() && !candidate.player() && !candidate.armorStand()) {
            entries.add(new EntityCatalog.Entry(candidate.id(), candidate.name()));
         }
      }

      return sorted(entries);
   }

   public static List<EntityCatalog.Entry> sorted(Collection<EntityCatalog.Entry> entries) {
      List<EntityCatalog.Entry> list = new ArrayList(entries);
      list.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)).thenComparing(EntityCatalog.Entry::id));
      return list;
   }

   public static List<EntityCatalog.Entry> filter(List<EntityCatalog.Entry> entries, String query) {
      String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
      if (needle.isEmpty()) {
         return List.copyOf(entries);
      }

      List<EntityCatalog.Entry> matches = new ArrayList();

      for (EntityCatalog.Entry entry : entries) {
         if (entry.name().toLowerCase(Locale.ROOT).contains(needle) || entry.id().toLowerCase(Locale.ROOT).contains(needle)) {
            matches.add(entry);
         }
      }

      return matches;
   }

   public static String nextState(String current) {
      if ("allow".equals(current)) {
         return "deny";
      } else {
         return "deny".equals(current) ? null : "allow";
      }
   }

   public static String stateKey(String current) {
      if ("allow".equals(current)) {
         return "smartaim.state.always";
      } else {
         return "deny".equals(current) ? "smartaim.state.never" : "smartaim.state.default";
      }
   }

   public record Candidate(String id, String name, boolean livingMob, boolean player, boolean armorStand) {
   }

   public record Entry(String id, String name) {
   }
}
