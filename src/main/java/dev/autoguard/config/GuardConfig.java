package dev.autoguard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent settings, with defaults and clamps applied on every load. */
public final class GuardConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoGuard");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("autoguard.json");
    public static final int CURRENT_SCHEMA = 2;

    public int schemaVersion = CURRENT_SCHEMA;
    public boolean enabled = true;
    public boolean allowRemoteServers = false;
    public boolean creative = false;
    public boolean melee = true;
    public boolean projectiles = true;
    public boolean creepers = false;
    public boolean hostileMobs = true;
    public boolean angryNeutralMobs = true;
    public boolean dontInterruptItemUse = true;
    public boolean playerInputPriority = true;
    public String preset = "Normal";
    public double customEarlyMargin = 1.25;
    public double customArcDegrees = 120.0;
    public double customMaxContinuousHoldSeconds = 0.8;
    public double customCooldownAfterReleaseSeconds = 0.6;
    public double customHoldAfterThreatGoneSeconds = 0.2;
    public double customAttackSuppressSeconds = 0.4;
    public double projectileArrivalTicks = 12.0;
    public int minimumDurability = 12;
    public boolean showHud = true;
    public String hudPosition = "TOP_LEFT";
    public boolean debug = false;
    public List<String> alwaysReactTo = new ArrayList<>();
    public List<String> neverReactTo = new ArrayList<>();
    private transient Set<Identifier> alwaysReactIds = Set.of();
    private transient Set<Identifier> neverReactIds = Set.of();

    public static GuardConfig load() {
        GuardConfig config = new GuardConfig();
        try {
            if (Files.exists(FILE)) {
                JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                // Gson leaves absent keys at Java defaults; merge onto defaults so legacy files are safe.
                JsonObject merged = GSON.toJsonTree(config).getAsJsonObject();
                json.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                GuardConfig parsed = GSON.fromJson(merged, GuardConfig.class);
                if (parsed != null) config = parsed;
                if (config.schemaVersion < CURRENT_SCHEMA) config.schemaVersion = CURRENT_SCHEMA;
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not read AutoGuard config; using defaults: {}", exception.toString());
            config = new GuardConfig();
        }
        config.sanitize();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException exception) {
            LOGGER.error("Could not save AutoGuard config: {}", exception.toString());
        }
    }

    public void sanitize() {
        schemaVersion = CURRENT_SCHEMA;
        if (!List.of("Cautious", "Normal", "Aggressive").contains(preset)) preset = "Normal";
        customEarlyMargin = clamp(customEarlyMargin, 0.0, 5.0, 1.25);
        customArcDegrees = clamp(customArcDegrees, 15.0, 180.0, 120.0);
        customMaxContinuousHoldSeconds = clamp(customMaxContinuousHoldSeconds, 0.1, 10.0, 0.8);
        customCooldownAfterReleaseSeconds = clamp(customCooldownAfterReleaseSeconds, 0.0, 10.0, 0.6);
        customHoldAfterThreatGoneSeconds = clamp(customHoldAfterThreatGoneSeconds, 0.0, 5.0, 0.2);
        customAttackSuppressSeconds = clamp(customAttackSuppressSeconds, 0.0, 5.0, 0.4);
        projectileArrivalTicks = clamp(projectileArrivalTicks, 1.0, 40.0, 12.0);
        minimumDurability = Math.max(0, Math.min(336, minimumDurability));
        if (!List.of("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT").contains(hudPosition)) hudPosition = "TOP_LEFT";
        if (alwaysReactTo == null) alwaysReactTo = new ArrayList<>();
        if (neverReactTo == null) neverReactTo = new ArrayList<>();
        alwaysReactIds = parseIds(alwaysReactTo);
        neverReactIds = parseIds(neverReactTo);
    }

    /** Blocks near a front-facing threat are raised when the hit is imminent (blocks). */
    public double earlyMargin() {
        return switch (preset) {
            case "Cautious" -> 2.0;
            case "Aggressive" -> 0.5;
            default -> customEarlyMargin;
        };
    }
    public double arcDegrees() {
        return switch (preset) {
            case "Cautious" -> 150.0;
            case "Aggressive" -> 75.0;
            default -> customArcDegrees;
        };
    }
    /** Maximum continuous seconds the mod may hold the shield up before a forced release. */
    public double maxContinuousHoldSeconds() {
        return switch (preset) {
            case "Cautious" -> 0.5;
            case "Aggressive" -> 1.2;
            default -> customMaxContinuousHoldSeconds;
        };
    }
    /** Seconds after a release during which the mod will not re-raise, even if a threat exists. */
    public double cooldownAfterReleaseSeconds() {
        return switch (preset) {
            case "Cautious" -> 1.0;
            case "Aggressive" -> 0.3;
            default -> customCooldownAfterReleaseSeconds;
        };
    }
    /** Seconds to keep the shield up after the threat is gone, then release. */
    public double holdAfterThreatGoneSeconds() {
        return switch (preset) {
            case "Cautious" -> 0.5;
            case "Aggressive" -> 0.1;
            default -> customHoldAfterThreatGoneSeconds;
        };
    }
    /** Seconds after any player attack during which the mod will not re-raise the shield. */
    public double attackSuppressSeconds() {
        return switch (preset) {
            case "Cautious" -> 0.6;
            case "Aggressive" -> 0.2;
            default -> customAttackSuppressSeconds;
        };
    }
    public double projectileArrivalTicks() { return projectileArrivalTicks; }
    public void setPreset(String value) {
        if (List.of("Cautious", "Normal", "Aggressive").contains(value)) preset = value;
        save();
    }
    public boolean alwaysReactTo(Identifier id) { return alwaysReactIds.contains(id); }
    public boolean neverReactTo(Identifier id) { return neverReactIds.contains(id); }

    private static Set<Identifier> parseIds(List<String> values) {
        Set<Identifier> parsed = new HashSet<>(values.size());
        for (String value : values) {
            if (value == null) continue;
            try {
                Identifier id = Identifier.tryParse(value);
                if (id != null) parsed.add(id);
            } catch (RuntimeException ignored) {
                // Ignore malformed entity IDs from hand-edited or older config files.
            }
        }
        return Set.copyOf(parsed);
    }

    private static double clamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
