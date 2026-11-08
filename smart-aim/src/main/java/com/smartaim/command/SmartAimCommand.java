// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.smartaim.Presets;
import com.smartaim.SmartAim;
import com.smartaim.SmartAimConfig;
import com.smartaim.aim.ProjectileKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SmartAimCommand {
   private SmartAimCommand() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, buildContext, selection) -> dispatcher.register(build()));
   }

   private static LiteralArgumentBuilder<CommandSourceStack> build() {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("smartaim").requires(SmartAimCommand::canUse);
      root.then(Commands.literal("status").executes(SmartAimCommand::status));
      root.then(Commands.literal("toggle").executes(SmartAimCommand::toggle));
      root.then(Commands.literal("tracking")
         .then(Commands.literal("on").executes(context -> setTracking(context, true)))
         .then(Commands.literal("off").executes(context -> setTracking(context, false))));
      root.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
         .executes(SmartAimCommand::reload));

      LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
      set.then(Commands.literal("range").then(number(1.0, 64.0, "range")));
      set.then(Commands.literal("maxTurnDegrees").then(number(0.0, 45.0, "maxTurnDegrees")));
      set.then(Commands.literal("coneAngle").then(number(1.0, 89.0, "coneAngle")));
      set.then(Commands.literal("trackingRange").then(number(1.0, 64.0, "trackingRange")));
      set.then(Commands.literal("trackingMaxTicks").then(number(1.0, 400.0, "trackingMaxTicks")));
      set.then(Commands.literal("trackingMaxAngle").then(number(1.0, 170.0, "trackingMaxAngle")));
      set.then(Commands.literal("trackingGiveUpTicks").then(number(1.0, 200.0, "trackingGiveUpTicks")));
      set.then(Commands.literal("trackingTeleportDistance").then(number(0.1, 64.0, "trackingTeleportDistance")));
      set.then(Commands.literal("trackingEnabled").then(toggle("trackingEnabled")));
      set.then(Commands.literal("leadTarget").then(toggle("leadTarget")));
      set.then(Commands.literal("debugParticles").then(toggle("debugParticles")));
      root.then(set);

      RequiredArgumentBuilder<CommandSourceStack, Identifier> entityId = Commands.argument("id", IdentifierArgument.id())
         .suggests(SmartAimCommand::suggestEntityIds)
         .then(Commands.literal("allow").executes(context -> entity(context, "allow")))
         .then(Commands.literal("deny").executes(context -> entity(context, "deny")))
         .then(Commands.literal("reset").executes(context -> entity(context, null)));
      root.then(Commands.literal("entity").then(entityId));

      LiteralArgumentBuilder<CommandSourceStack> categories = Commands.literal("category");
      categories.then(category("hostile"));
      categories.then(category("neutral"));
      categories.then(category("friendly"));
      categories.then(category("tamedPets"));
      root.then(categories);

      RequiredArgumentBuilder<CommandSourceStack, String> projectileId = Commands.argument("id", StringArgumentType.word())
         .suggests(SmartAimCommand::suggestProjectileIds)
         .then(Commands.literal("on").executes(context -> projectile(context, true)))
         .then(Commands.literal("off").executes(context -> projectile(context, false)));
      root.then(Commands.literal("projectile").then(projectileId));

      LiteralArgumentBuilder<CommandSourceStack> presets = Commands.literal("preset");
      presets.then(Commands.literal("mild").executes(context -> preset(context, Presets.MILD)));
      presets.then(Commands.literal("normal").executes(context -> preset(context, Presets.NORMAL)));
      presets.then(Commands.literal("strong").executes(context -> preset(context, Presets.STRONG)));
      root.then(presets);
      return root;
   }

   public static boolean canUse(CommandSourceStack source) {
      return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source) || isSingleplayer(source);
   }

   private static boolean isSingleplayer(CommandSourceStack source) {
      MinecraftServer server = source.getServer();
      return server != null && server.isSingleplayer();
   }

   private static RequiredArgumentBuilder<CommandSourceStack, Double> number(double min, double max, String field) {
      return (RequiredArgumentBuilder<CommandSourceStack, Double>)Commands.argument("value", DoubleArgumentType.doubleArg(min, max))
         .executes(context -> setNumber(context, field));
   }

   private static RequiredArgumentBuilder<CommandSourceStack, Boolean> toggle(String key) {
      return (RequiredArgumentBuilder<CommandSourceStack, Boolean>)Commands.argument("value", BoolArgumentType.bool())
         .executes(context -> setBool(context, key));
   }

   private static LiteralArgumentBuilder<CommandSourceStack> category(String name) {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal(name)
            .then(Commands.literal("on").executes(context -> setCategory(context, name, true))))
         .then(Commands.literal("off").executes(context -> setCategory(context, name, false)));
   }

   private static int reload(CommandContext<CommandSourceStack> context) {
      SmartAimConfig config = SmartAimConfig.load();
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.translatable("smartaim.command.reloaded", new Object[]{config.summary()}), true);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> context) {
      SmartAimConfig config = SmartAimConfig.get();
      Component onOff = onOffWord(config.enabled);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.translatable("smartaim.command.status.header", new Object[]{onOff}), false);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(() -> Component.translatable("smartaim.command.status.build", new Object[]{config.range, config.maxTurnDegrees, config.coneAngle}), false);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.translatable("smartaim.command.status.tracking",
         new Object[]{onOffWord(config.trackingEnabled), config.trackingRange, config.trackingMaxTicks / 20.0, config.trackingMaxAngle,
            config.trackingGiveUpTicks, config.trackingTeleportDistance}), false);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(
            () -> Component.translatable(
               "smartaim.command.status.categories",
               new Object[]{
                  onOffWord(config.categories.hostile),
                  onOffWord(config.categories.neutral),
                  onOffWord(config.categories.friendly),
                  onOffWord(config.categories.tamedPets),
                  config.overrideCount()
               }
            ),
            false
         );
      ((CommandSourceStack)context.getSource())
         .sendSuccess(() -> Component.translatable("smartaim.command.status.projectiles", new Object[]{projectileList(config, true)}), false);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(() -> Component.translatable("smartaim.command.status.projectiles_off", new Object[]{projectileList(config, false)}), false);
      return config.enabled ? 1 : 0;
   }

   private static int setTracking(CommandContext<CommandSourceStack> context, boolean enabled) {
      SmartAimConfig config = SmartAimConfig.get();
      config.trackingEnabled = enabled;
      SmartAimConfig.apply(config);
      return report(context, "smartaim.command.tracking", onOffWord(enabled).getString());
   }

   private static int toggle(CommandContext<CommandSourceStack> context) {
      SmartAimConfig config = SmartAimConfig.get();
      config.enabled = !config.enabled;
      SmartAimConfig.apply(config);
      Component message = Component.translatable("smartaim.command.toggled", new Object[]{onOffWord(config.enabled)});
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> message, false);
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         player.sendOverlayMessage(message);
      }

      SmartAim.LOGGER.info("Assist toggled by {} -> {}", ((CommandSourceStack)context.getSource()).getTextName(), config.enabled);
      return config.enabled ? 1 : 0;
   }

   private static int setNumber(CommandContext<CommandSourceStack> context, String field) {
      double value = DoubleArgumentType.getDouble(context, "value");
      SmartAimConfig config = SmartAimConfig.get();
      switch (field) {
         case "range":
            config.range = value;
            break;
         case "maxTurnDegrees":
            config.maxTurnDegrees = value;
            break;
         case "coneAngle":
            config.coneAngle = value;
            break;
         case "trackingRange":
            config.trackingRange = value;
            break;
         case "trackingMaxTicks":
            config.trackingMaxTicks = (int)Math.round(value);
            break;
         case "trackingMaxAngle":
            config.trackingMaxAngle = value;
            break;
         case "trackingGiveUpTicks":
            config.trackingGiveUpTicks = (int)Math.round(value);
            break;
         case "trackingTeleportDistance":
            config.trackingTeleportDistance = value;
            break;
         default:
            return 0;
      }

      SmartAimConfig.apply(config);

      String applied = switch (field) {
         case "range" -> format(config.range);
         case "maxTurnDegrees" -> format(config.maxTurnDegrees);
         case "coneAngle" -> format(config.coneAngle);
         case "trackingRange" -> format(config.trackingRange);
         case "trackingMaxTicks" -> Integer.toString(config.trackingMaxTicks);
         case "trackingMaxAngle" -> format(config.trackingMaxAngle);
         case "trackingGiveUpTicks" -> Integer.toString(config.trackingGiveUpTicks);
         default -> format(config.trackingTeleportDistance);
      };
      return report(context, "smartaim.command.set", field, applied);
   }

   private static int setBool(CommandContext<CommandSourceStack> context, String key) {
      boolean value = BoolArgumentType.getBool(context, "value");
      SmartAimConfig config = SmartAimConfig.get();
      switch (key) {
         case "trackingEnabled":
            config.trackingEnabled = value;
            break;
         case "leadTarget":
            config.leadTarget = value;
            break;
         case "debugParticles":
            config.debugParticles = value;
            break;
         default:
            return 0;
      }

      SmartAimConfig.apply(config);
      return report(context, "smartaim.command.set", key, onOffWord(value).getString());
   }

   private static int entity(CommandContext<CommandSourceStack> context, String action) {
      Identifier id = IdentifierArgument.getId(context, "id");
      if (BuiltInRegistries.ENTITY_TYPE.getOptional(id).isEmpty()) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.translatable("smartaim.command.unknown_entity", new Object[]{id.toString()}));
         return 0;
      } else {
         SmartAimConfig config = SmartAimConfig.get();
         config.setOverrideById(id.toString(), action);
         SmartAimConfig.apply(config);
         String state = action == null ? Component.translatable("smartaim.state.default").getString() : action;
         ((CommandSourceStack)context.getSource())
            .sendSuccess(() -> Component.translatable("smartaim.command.entity", new Object[]{id.toString(), state}), true);
         return 1;
      }
   }

   private static int setCategory(CommandContext<CommandSourceStack> context, String name, boolean value) {
      SmartAimConfig config = SmartAimConfig.get();
      switch (name) {
         case "hostile":
            config.categories.hostile = value;
            break;
         case "neutral":
            config.categories.neutral = value;
            break;
         case "friendly":
            config.categories.friendly = value;
            break;
         case "tamedPets":
            config.categories.tamedPets = value;
            break;
         default:
            return 0;
      }

      SmartAimConfig.apply(config);
      return report(context, "smartaim.command.category", name, onOffWord(value).getString());
   }

   private static int projectile(CommandContext<CommandSourceStack> context, boolean enabled) {
      String raw = StringArgumentType.getString(context, "id").toLowerCase(Locale.ROOT);
      ProjectileKind kind = ProjectileKind.byId(raw);
      if (kind == null) {
         ((CommandSourceStack)context.getSource()).sendFailure(Component.translatable("smartaim.command.unknown_projectile", new Object[]{raw}));
         return 0;
      }

      SmartAimConfig config = SmartAimConfig.get();
      if (config.projectiles == null) {
         config.projectiles = new LinkedHashMap();
      }

      config.projectiles.put(kind.id(), enabled);
      SmartAimConfig.apply(config);
      return report(context, "smartaim.command.projectile", kind.id(), onOffWord(enabled).getString());
   }

   private static int preset(CommandContext<CommandSourceStack> context, Presets preset) {
      SmartAimConfig config = SmartAimConfig.get();
      preset.applyTo(config);
      SmartAimConfig.apply(config);
      ((CommandSourceStack)context.getSource())
         .sendSuccess(
            () -> Component.translatable(
               "smartaim.command.preset",
               new Object[]{Component.translatable(preset.translationKey()), format(config.range), format(config.maxTurnDegrees), format(config.coneAngle)}
            ),
            true
         );
      return 1;
   }

   private static int report(CommandContext<CommandSourceStack> context, String key, Object... arguments) {
      Component message = Component.translatable(key, arguments);
      ((CommandSourceStack)context.getSource()).sendSuccess(() -> message, true);
      ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayer();
      if (player != null) {
         player.sendOverlayMessage(message);
      }

      return 1;
   }

   private static String format(double value) {
      return value == Math.rint(value) ? String.valueOf((long)value) : String.format(Locale.ROOT, "%.1f", new Object[]{value});
   }

   private static Component onOffWord(boolean value) {
      return value ? Component.translatable("smartaim.text.on") : Component.translatable("smartaim.text.off");
   }

   private static CompletableFuture<Suggestions> suggestEntityIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      List<String> ids = new ArrayList();

      for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
         ids.add(id.toString());
      }

      return SharedSuggestionProvider.suggest(ids, builder);
   }

   private static CompletableFuture<Suggestions> suggestProjectileIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      List<String> ids = new ArrayList();

      for (ProjectileKind kind : ProjectileKind.values()) {
         ids.add(kind.id());
      }

      return SharedSuggestionProvider.suggest(ids, builder);
   }

   private static String projectileList(SmartAimConfig config, boolean enabled) {
      StringBuilder builder = new StringBuilder();

      for (ProjectileKind kind : ProjectileKind.values()) {
         if (config.isProjectileEnabled(kind) == enabled) {
            if (builder.length() > 0) {
               builder.append(", ");
            }

            builder.append(kind.id());
         }
      }

      return builder.length() == 0 ? "-" : builder.toString();
   }
}
