// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.smartaim.aim.ProjectileKind;
import com.smartaim.aim.TargetRules;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartAimConfig {
   public static final String FILE_NAME = "smartaim.json";
   public static final String TEMP_NAME = "smartaim.json.tmp";
   public static final String BACKUP_NAME = "smartaim.json.bak";
   public static final int CURRENT_VERSION = 3;
   private static final Logger LOGGER = LoggerFactory.getLogger("smartaim/config");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static volatile SmartAimConfig current = new SmartAimConfig();
   public int configVersion = 3;
   public boolean enabled = true;
   public boolean trackingEnabled = false;
   public double trackingRange = 32.0;
   public int trackingMaxTicks = 100;
   public double trackingMaxAngle = 100.0;
   public int trackingGiveUpTicks = 15;
   public double trackingTeleportDistance = 6.0;
   public double range = 24.0;
   public double maxTurnDegrees = 10.0;
   public double coneAngle = 20.0;
   public boolean leadTarget = true;
   public boolean debugParticles = false;
   public SmartAimConfig.Categories categories = new SmartAimConfig.Categories();
   public Map<String, String> entityOverrides = new LinkedHashMap();
   public Map<String, Boolean> projectiles = ProjectileKind.defaults();
   @SerializedName("targets")
   private String legacyTargets;

   public static SmartAimConfig get() {
      return current;
   }

   public static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("smartaim.json");
   }

   public static String entityId(EntityType<?> type) {
      Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
      return key == null ? "" : key.toString();
   }

   public String overrideFor(EntityType<?> type) {
      return this.overrideForId(entityId(type));
   }

   public String overrideForId(String id) {
      return this.entityOverrides != null && !this.entityOverrides.isEmpty() && id != null ? (String)this.entityOverrides.get(id) : null;
   }

   public void setOverride(EntityType<?> type, String action) {
      this.setOverrideById(entityId(type), action);
   }

   public void setOverrideById(String id, String action) {
      if (id != null && !id.isEmpty()) {
         if (this.entityOverrides == null) {
            this.entityOverrides = new LinkedHashMap();
         }

         if (action == null) {
            this.entityOverrides.remove(id);
         } else {
            this.entityOverrides.put(id, action);
         }
      }
   }

   public boolean isProjectileEnabled(ProjectileKind kind) {
      if (kind != null && this.projectiles != null) {
         Boolean value = (Boolean)this.projectiles.get(kind.id());
         return value != null ? value : kind.enabledByDefault();
      } else {
         return false;
      }
   }

   public int overrideCount() {
      return this.entityOverrides == null ? 0 : this.entityOverrides.size();
   }

   public SmartAimConfig copy() {
      SmartAimConfig copy = new SmartAimConfig();
      copy.configVersion = this.configVersion;
      copy.enabled = this.enabled;
      copy.trackingEnabled = this.trackingEnabled;
      copy.trackingRange = this.trackingRange;
      copy.trackingMaxTicks = this.trackingMaxTicks;
      copy.trackingMaxAngle = this.trackingMaxAngle;
      copy.trackingGiveUpTicks = this.trackingGiveUpTicks;
      copy.trackingTeleportDistance = this.trackingTeleportDistance;
      copy.range = this.range;
      copy.maxTurnDegrees = this.maxTurnDegrees;
      copy.coneAngle = this.coneAngle;
      copy.leadTarget = this.leadTarget;
      copy.debugParticles = this.debugParticles;
      copy.categories = new SmartAimConfig.Categories();
      copy.categories.hostile = this.categories.hostile;
      copy.categories.neutral = this.categories.neutral;
      copy.categories.friendly = this.categories.friendly;
      copy.categories.tamedPets = this.categories.tamedPets;
      copy.entityOverrides = new LinkedHashMap(this.entityOverrides == null ? Map.of() : this.entityOverrides);
      copy.projectiles = new LinkedHashMap(this.projectiles == null ? Map.of() : this.projectiles);
      return copy;
   }

   public static SmartAimConfig fromJson(String json) {
      if (json != null && !json.isBlank()) {
         JsonElement element;
         try {
            element = JsonParser.parseString(json);
         } catch (RuntimeException exception) {
            LOGGER.warn("Malformed config JSON, using defaults", exception);
            SmartAimConfig defaults = new SmartAimConfig();
            defaults.sanitise();
            return defaults;
         }

         boolean hasCategories = element.isJsonObject() && element.getAsJsonObject().has("categories");
         SmartAimConfig config = (SmartAimConfig)GSON.fromJson(element, SmartAimConfig.class);
         if (config == null) {
            config = new SmartAimConfig();
         }

         if (!hasCategories) {
            config.categories = null;
         }

         config.sanitise();
         return config;
      } else {
         SmartAimConfig defaults = new SmartAimConfig();
         defaults.sanitise();
         return defaults;
      }
   }

   public String toJson() {
      return GSON.toJson(this);
   }

   public static synchronized SmartAimConfig apply(SmartAimConfig config) {
      config.sanitise();
      current = config;
      save(config);
      return config;
   }

   public static synchronized SmartAimConfig load() {
      SmartAimConfig config = loadFrom(file());
      current = config;
      LOGGER.info("Loaded config: {}", config);
      return config;
   }

   static SmartAimConfig loadFrom(Path path) {
      if (Files.notExists(path, new LinkOption[0])) {
         SmartAimConfig defaults = new SmartAimConfig();
         defaults.sanitise();
         saveTo(path, defaults);
         LOGGER.info("Created default config at {}", path);
         return defaults;
      }

      String text;
      try {
         text = Files.readString(path, StandardCharsets.UTF_8);
      } catch (IOException exception) {
         LOGGER.warn("Could not read {} - keeping a copy in {} and using defaults", new Object[]{path, backupOf(path), exception});
         backup(path);
         SmartAimConfig defaults = new SmartAimConfig();
         defaults.sanitise();
         return defaults;
      }

      if (!isParseable(text)) {
         LOGGER.warn("{} is not valid JSON - keeping a copy in {} and using defaults", path, backupOf(path));
         backup(path);
      }

      return fromJson(text);
   }

   public static boolean isParseable(String json) {
      if (json != null && !json.isBlank()) {
         try {
            JsonParser.parseString(json);
            return true;
         } catch (RuntimeException exception) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static Path backupOf(Path path) {
      return path.resolveSibling("smartaim.json.bak");
   }

   static void backup(Path path) {
      try {
         Files.copy(path, backupOf(path), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
         LOGGER.warn("Backed up {} to {}", path, backupOf(path));
      } catch (IOException exception) {
         LOGGER.warn("Could not back up {} to {}", new Object[]{path, backupOf(path), exception});
      }
   }

   public static synchronized void save(SmartAimConfig config) {
      saveTo(file(), config);
   }

   public static void saveTo(Path path, SmartAimConfig config) {
      try {
         Files.createDirectories(path.getParent(), new FileAttribute[0]);
         Path temp = path.resolveSibling("smartaim.json.tmp");
         Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, new OpenOption[0]);

         try {
            writer.write(config.toJson());
         } catch (Throwable var8) {
            if (writer != null) {
               try {
                  writer.close();
               } catch (Throwable var6) {
                  var8.addSuppressed(var6);
               }
            }

            throw var8;
         }

         if (writer != null) {
            writer.close();
         }

         try {
            Files.move(temp, path, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE});
         } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, path, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
         }
      } catch (IOException exception) {
         LOGGER.warn("Could not write {}", path, exception);
      }
   }

   public void sanitise() {
      if (!Double.isFinite(this.trackingRange)) {
         this.trackingRange = 32.0;
      }

      if (!Double.isFinite(this.trackingMaxAngle)) {
         this.trackingMaxAngle = 100.0;
      }

      if (!Double.isFinite(this.trackingTeleportDistance)) {
         this.trackingTeleportDistance = 6.0;
      }

      this.trackingRange = Math.max(1.0, Math.min(64.0, this.trackingRange));
      this.trackingMaxTicks = Math.max(1, Math.min(400, this.trackingMaxTicks));
      this.trackingMaxAngle = Math.max(1.0, Math.min(170.0, this.trackingMaxAngle));
      this.trackingGiveUpTicks = Math.max(1, Math.min(200, this.trackingGiveUpTicks));
      this.trackingTeleportDistance = Math.max(0.1, Math.min(64.0, this.trackingTeleportDistance));

      if (!Double.isFinite(this.range)) {
         this.range = 24.0;
      }

      if (!Double.isFinite(this.maxTurnDegrees)) {
         this.maxTurnDegrees = 10.0;
      }

      if (!Double.isFinite(this.coneAngle)) {
         this.coneAngle = 20.0;
      }

      this.range = Math.max(1.0, Math.min(64.0, this.range));
      this.maxTurnDegrees = Math.max(0.0, Math.min(45.0, this.maxTurnDegrees));
      this.coneAngle = Math.max(1.0, Math.min(89.0, this.coneAngle));
      this.migrateLegacyTargets();
      if (this.categories == null) {
         this.categories = new SmartAimConfig.Categories();
      }

      this.entityOverrides = normaliseOverrides(this.entityOverrides);
      this.projectiles = normaliseProjectiles(this.projectiles);
      if (this.configVersion < 3) {
         this.configVersion = 3;
      }
   }

   private void migrateLegacyTargets() {
      if (this.legacyTargets != null) {
         String mode = this.legacyTargets.trim().toLowerCase(Locale.ROOT);
         if (this.categories == null) {
            this.categories = new SmartAimConfig.Categories();
            switch (mode) {
               case "hostile_and_neutral":
                  this.categories.hostile = true;
                  this.categories.neutral = true;
                  this.categories.friendly = false;
                  break;
               case "all_mobs":
                  this.categories.hostile = true;
                  this.categories.neutral = true;
                  this.categories.friendly = true;
                  this.categories.tamedPets = false;
                  if (this.entityOverrides == null) {
                     this.entityOverrides = new LinkedHashMap();
                  }

                  for (String id : TargetRules.ALL_MOBS_DENIED) {
                     this.entityOverrides.putIfAbsent(id, "deny");
                  }
                  break;
               default:
                  this.categories.hostile = true;
                  this.categories.neutral = false;
                  this.categories.friendly = false;
            }

            LOGGER.info("Migrated 1.1.0 targets '{}' to categories: {}", this.legacyTargets, this.categories);
         } else {
            LOGGER.info("Ignoring legacy targets '{}': explicit categories are present", this.legacyTargets);
         }

         this.legacyTargets = null;
      }
   }

   private static Map<String, String> normaliseOverrides(Map<String, String> input) {
      Map<String, String> out = new LinkedHashMap();
      if (input == null) {
         return out;
      }

      input.forEach((key, value) -> {
         String id = TargetRules.normaliseEntityId(key);
         String action = TargetRules.normaliseAction(value);
         if (id != null && action != null) {
            out.put(canonicalEntityId(id), action);
         } else {
            LOGGER.warn("Dropping invalid entity override '{}': '{}'", key, value);
         }
      });
      return out;
   }

   static String canonicalEntityId(String id) {
      return id.indexOf(58) >= 0 ? id : "minecraft:" + id;
   }

   private static Map<String, Boolean> normaliseProjectiles(Map<String, Boolean> input) {
      Map<String, Boolean> out = new LinkedHashMap();

      for (ProjectileKind kind : ProjectileKind.values()) {
         Boolean value = input == null ? null : (Boolean)input.get(kind.id());
         out.put(kind.id(), value != null ? value : kind.enabledByDefault());
      }

      return out;
   }

   public String summary() {
      StringBuilder projectilesOn = new StringBuilder();

      for (ProjectileKind kind : ProjectileKind.values()) {
         if (this.isProjectileEnabled(kind)) {
            if (projectilesOn.length() > 0) {
               projectilesOn.append(',');
            }

            projectilesOn.append(kind.id());
         }
      }

      return "enabled="
         + this.enabled
         + ", range="
         + this.range
         + ", trackingEnabled="
         + this.trackingEnabled
         + ", trackingRange="
         + this.trackingRange
         + ", trackingMaxTicks="
         + this.trackingMaxTicks
         + ", trackingMaxAngle="
         + this.trackingMaxAngle
         + ", trackingGiveUpTicks="
         + this.trackingGiveUpTicks
         + ", trackingTeleportDistance="
         + this.trackingTeleportDistance
         + ", maxTurnDegrees="
         + this.maxTurnDegrees
         + ", coneAngle="
         + this.coneAngle
         + ", leadTarget="
         + this.leadTarget
         + ", debugParticles="
         + this.debugParticles
         + ", categories={"
         + this.categories
         + "}, overrides="
         + this.overrideCount()
         + ", projectiles=["
         + projectilesOn
         + "]";
   }

   public String toString() {
      return "configVersion=" + this.configVersion + ", " + this.summary();
   }

   public static final class Categories {
      public boolean hostile = true;
      public boolean neutral = false;
      public boolean friendly = false;
      public boolean tamedPets = false;

      public String toString() {
         return "hostile=" + this.hostile + ", neutral=" + this.neutral + ", friendly=" + this.friendly + ", tamedPets=" + this.tamedPets;
      }
   }
}
