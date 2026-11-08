// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.client;

import com.smartaim.Presets;
import com.smartaim.SmartAimConfig;
import com.smartaim.aim.ProjectileKind;
import com.smartaim.client.layout.Box;
import com.smartaim.client.layout.ConfigLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class SmartAimConfigScreen extends Screen {
   private static final int PAGE_GENERAL = 0;
   private static final int PAGE_TARGETS = 1;
   private static final int PAGE_PROJECTILES = 2;
   private static final int COLUMNS = 2;
   private static final int COLOR_TEXT = -1;
   private static final int COLOR_DIM = -6250336;
   private static final int COLOR_WARNING = -43691;
   private final Screen parentScreen;
   private SmartAimConfig draft;
   private int page = 0;
   private int scroll;

   public SmartAimConfigScreen(Screen parentScreen) {
      super(Component.translatable("smartaim.screen.title"));
      this.parentScreen = parentScreen;
      this.draft = SmartAimConfig.get().copy();
   }

   public SmartAimConfig draft() {
      return this.draft;
   }

   protected void init() {
      ConfigLayout.Result layout = this.layout();
      this.scroll = layout.scroll();
      List<SmartAimConfigScreen.Row> rows = this.rows();

      for (int index = 0; index < layout.tabs().size(); index++) {
         Box tab = (Box)layout.tabs().get(index);
         int target = index;
         this.addRenderableWidget(Button.builder(tabLabel(index), button -> this.switchPage(target)).bounds(tab.x(), tab.y(), tab.w(), tab.h()).build());
      }

      for (ConfigLayout.RowSlot slot : layout.rows()) {
         if (slot.index() < rows.size()) {
            for (AbstractWidget widget : ((SmartAimConfigScreen.Row)rows.get(slot.index())).create(slot.box())) {
               this.addRenderableWidget(widget);
            }
         }
      }

      if (layout.scrollUp() != null) {
         Box up = layout.scrollUp();
         this.addRenderableWidget(
            Button.builder(Component.translatable("smartaim.button.up"), button -> this.scrollBy(-1)).bounds(up.x(), up.y(), up.w(), up.h()).build()
         );
      }

      if (layout.scrollDown() != null) {
         Box down = layout.scrollDown();
         this.addRenderableWidget(
            Button.builder(Component.translatable("smartaim.button.down"), button -> this.scrollBy(1)).bounds(down.x(), down.y(), down.w(), down.h()).build()
         );
      }

      Box reset = layout.reset();
      Box cancel = layout.cancel();
      Box save = layout.save();
      this.addRenderableWidget(
         Button.builder(Component.translatable("smartaim.button.reset"), button -> this.resetToDefaults())
            .bounds(reset.x(), reset.y(), reset.w(), reset.h())
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("smartaim.button.cancel"), button -> this.closeToParent())
            .bounds(cancel.x(), cancel.y(), cancel.w(), cancel.h())
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("smartaim.button.save"), button -> this.saveAndClose()).bounds(save.x(), save.y(), save.w(), save.h()).build()
      );
   }

   private ConfigLayout.Result layout() {
      return ConfigLayout.compute(this.width, this.height, new ConfigLayout.Options(this.isRemote(), this.helpLines(), this.rows().size(), 2, this.scroll));
   }

   private void switchPage(int newPage) {
      this.page = newPage;
      this.scroll = 0;
      this.rebuildWidgets();
   }

   private void scrollBy(int delta) {
      int next = Math.max(0, Math.min(this.layout().maxScroll(), this.scroll + delta));
      if (next != this.scroll) {
         this.scroll = next;
         this.rebuildWidgets();
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (scrollY != 0.0 && this.layout().maxScroll() > 0) {
         this.scrollBy(scrollY > 0.0 ? -1 : 1);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   private boolean isRemote() {
      return this.minecraft.getConnection() != null && !this.minecraft.hasSingleplayerServer();
   }

   private Component helpText() {
      return switch (this.page) {
         case 1 -> this.draft.categories.tamedPets
            ? Component.translatable("smartaim.note.tamed_pets")
            : Component.translatable("smartaim.note.never_targeted");
         case 2 -> this.draft.isProjectileEnabled(ProjectileKind.FISHING_BOBBER)
            ? Component.translatable("smartaim.note.projectiles")
               .append(Component.literal("\n"))
               .append(Component.translatable("smartaim.note.fishing_bobber"))
            : Component.translatable("smartaim.note.projectiles");
         default -> null;
      };
   }

   private int helpLines() {
      Component help = this.helpText();
      if (help == null) {
         return 0;
      }

      int textWidth = Math.max(20, this.width - 8);
      return Math.min(3, this.font.split(help, textWidth).size());
   }

   private List<SmartAimConfigScreen.Row> rows() {
      List<SmartAimConfigScreen.Row> rows = new ArrayList();
      switch (this.page) {
         case 0:
            rows.add(new SmartAimConfigScreen.ToggleRow("smartaim.option.enabled", () -> this.draft.enabled, value -> this.draft.enabled = value));
            rows.add(new SmartAimConfigScreen.ToggleRow("smartaim.option.tracking", () -> this.draft.trackingEnabled, value -> this.draft.trackingEnabled = value));
            rows.add(new SmartAimConfigScreen.SliderRow("smartaim.option.tracking_range", 1.0, 64.0, 1.0, () -> this.draft.trackingRange, value -> this.draft.trackingRange = value));
            rows.add(new SmartAimConfigScreen.SliderRow("smartaim.option.tracking_duration", 1.0, 20.0, 1.0, () -> this.draft.trackingMaxTicks / 20.0, value -> this.draft.trackingMaxTicks = (int)Math.round(value * 20.0)));
            rows.add(new SmartAimConfigScreen.SliderRow("smartaim.option.tracking_angle", 1.0, 170.0, 1.0, () -> this.draft.trackingMaxAngle, value -> this.draft.trackingMaxAngle = value));
            rows.add(new SmartAimConfigScreen.SliderRow("smartaim.option.range", 1.0, 64.0, 1.0, () -> this.draft.range, value -> this.draft.range = value));
            rows.add(
               new SmartAimConfigScreen.SliderRow(
                  "smartaim.option.max_turn", 0.0, 45.0, 0.5, () -> this.draft.maxTurnDegrees, value -> this.draft.maxTurnDegrees = value
               )
            );
            rows.add(
               new SmartAimConfigScreen.SliderRow("smartaim.option.cone", 1.0, 89.0, 1.0, () -> this.draft.coneAngle, value -> this.draft.coneAngle = value)
            );
            rows.add(new SmartAimConfigScreen.ToggleRow("smartaim.option.lead_target", () -> this.draft.leadTarget, value -> this.draft.leadTarget = value));
            rows.add(
               new SmartAimConfigScreen.ToggleRow(
                  "smartaim.option.debug_particles", () -> this.draft.debugParticles, value -> this.draft.debugParticles = value
               )
            );
            rows.add(new SmartAimConfigScreen.PresetRow(this));
            break;
         case 1:
            rows.add(
               new SmartAimConfigScreen.ToggleRow(
                  "smartaim.option.hostile", () -> this.draft.categories.hostile, value -> this.draft.categories.hostile = value
               )
            );
            rows.add(
               new SmartAimConfigScreen.ToggleRow(
                  "smartaim.option.neutral", () -> this.draft.categories.neutral, value -> this.draft.categories.neutral = value
               )
            );
            rows.add(
               new SmartAimConfigScreen.ToggleRow(
                  "smartaim.option.friendly", () -> this.draft.categories.friendly, value -> this.draft.categories.friendly = value
               )
            );
            BooleanSupplier var9 = () -> this.draft.categories.tamedPets;
            Consumer var10 = value -> this.draft.categories.tamedPets = value;
            SmartAimConfigScreen var8 = this;
            rows.add(new SmartAimConfigScreen.ToggleRowUpdatingHelp("smartaim.option.tamed_pets", var9, var10, () -> var8.rebuildWidgets()));
            rows.add(
               new SmartAimConfigScreen.ActionRow(
                  Component.translatable("smartaim.button.entity_list"), () -> this.minecraft.gui.setScreen(new SmartAimEntityListScreen(this))
               )
            );
            break;
         default:
            for (ProjectileKind kind : ProjectileKind.values()) {
               String key = "smartaim.projectile." + kind.id();
               if (kind == ProjectileKind.FISHING_BOBBER) {
                  BooleanSupplier var10004 = () -> this.draft.isProjectileEnabled(kind);
                  Consumer var10005 = value -> this.draft.projectiles.put(kind.id(), value);
                  SmartAimConfigScreen var7 = this;
                  rows.add(new SmartAimConfigScreen.ToggleRowUpdatingHelp(key, var10004, var10005, () -> var7.rebuildWidgets(), "smartaim.note.fishing_bobber"));
               } else {
                  rows.add(
                     new SmartAimConfigScreen.ToggleRow(key, () -> this.draft.isProjectileEnabled(kind), value -> this.draft.projectiles.put(kind.id(), value))
                  );
               }
            }
      }

      return rows;
   }

   private static Component tabLabel(int index) {
      return switch (index) {
         case 1 -> Component.translatable("smartaim.tab.targets");
         case 2 -> Component.translatable("smartaim.tab.projectiles");
         default -> Component.translatable("smartaim.tab.general");
      };
   }

   private static Component onOff(boolean value) {
      return Component.translatable(value ? "smartaim.text.on" : "smartaim.text.off");
   }

   private static Component label(String key, Component value) {
      return Component.translatable("smartaim.format.label_value", new Object[]{Component.translatable(key), value});
   }

   private void resetToDefaults() {
      this.draft = new SmartAimConfig();
      this.scroll = 0;
      this.rebuildWidgets();
   }

   private void saveAndClose() {
      SmartAimConfig.apply(this.draft);
      this.closeToParent();
   }

   private void closeToParent() {
      this.minecraft.gui.setScreen(this.parentScreen);
   }

   public void onClose() {
      this.closeToParent();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      ConfigLayout.Result layout = this.layout();
      Box title = layout.title();
      graphics.centeredText(this.font, this.title, title.centreX(), title.y(), -1);
      if (layout.notice() != null) {
         Box notice = layout.notice();
         graphics.text(this.font, this.clip(Component.translatable("smartaim.note.remote"), notice.w()), notice.x(), notice.y(), -43691);
      }

      Box help = layout.help();
      Component helpText = this.helpText();
      if (help != null && helpText != null) {
         List<FormattedCharSequence> lines = this.font.split(helpText, help.w());
         int maxLines = help.h() / 9;

         for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
            graphics.text(this.font, (FormattedCharSequence)lines.get(index), help.x(), help.y() + index * 9, -6250336);
         }
      }
   }

   private Component clip(Component text, int width) {
      return Component.literal(this.font.plainSubstrByWidth(text.getString(), Math.max(0, width)));
   }

   private static Button toggle(Box box, Component label, String tooltipKey, Runnable onClick) {
      Builder builder = Button.builder(label, pressed -> {
         onClick.run();
         pressed.setMessage(label);
      }).bounds(box.x(), box.y(), box.w(), box.h());
      if (tooltipKey != null) {
         builder = builder.tooltip(Tooltip.create(Component.translatable(tooltipKey)));
      }

      return builder.build();
   }

   private record ActionRow(Component label, Runnable action) implements SmartAimConfigScreen.Row {
      @Override
      public List<AbstractWidget> create(Box box) {
         return List.of(Button.builder(this.label, button -> this.action.run()).bounds(box.x(), box.y(), box.w(), box.h()).build());
      }
   }

   private record PresetRow(SmartAimConfigScreen screen) implements SmartAimConfigScreen.Row {
      @Override
      public List<AbstractWidget> create(Box box) {
         Presets[] presets = Presets.values();
         int gap = 2;
         int width = Math.max(20, (box.w() - gap * (presets.length - 1)) / presets.length);
         List<AbstractWidget> widgets = new ArrayList();

         for (int index = 0; index < presets.length; index++) {
            Presets preset = presets[index];
            int x = box.x() + index * (width + gap);
            widgets.add(Button.builder(Component.translatable(preset.translationKey()), button -> {
               preset.applyTo(this.screen.draft);
               this.screen.rebuildWidgets();
            }).bounds(x, box.y(), width, box.h()).build());
         }

         return widgets;
      }
   }

   private interface Row {
      List<AbstractWidget> create(Box var1);
   }

   private record SliderRow(String key, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter) implements SmartAimConfigScreen.Row {
      @Override
      public List<AbstractWidget> create(Box box) {
         return List.of(new SmartAimConfigScreen.ValueSlider(box, this));
      }
   }

   private record ToggleRow(String key, BooleanSupplier getter, Consumer<Boolean> setter, String tooltipKey) implements SmartAimConfigScreen.Row {
      ToggleRow(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
         this(key, getter, setter, null);
      }

      @Override
      public List<AbstractWidget> create(Box box) {
         return List.of(SmartAimConfigScreen.toggle(box, this.label(), this.tooltipKey, () -> this.setter.accept(!this.getter.getAsBoolean())));
      }

      private Component label() {
         return SmartAimConfigScreen.label(this.key, SmartAimConfigScreen.onOff(this.getter.getAsBoolean()));
      }
   }

   private record ToggleRowUpdatingHelp(String key, BooleanSupplier getter, Consumer<Boolean> setter, Runnable afterChange, String tooltipKey)
      implements SmartAimConfigScreen.Row {
      ToggleRowUpdatingHelp(String key, BooleanSupplier getter, Consumer<Boolean> setter, Runnable afterChange) {
         this(key, getter, setter, afterChange, null);
      }

      @Override
      public List<AbstractWidget> create(Box box) {
         return List.of(
            SmartAimConfigScreen.toggle(
               box, SmartAimConfigScreen.label(this.key, SmartAimConfigScreen.onOff(this.getter.getAsBoolean())), this.tooltipKey, () -> {
                  this.setter.accept(!this.getter.getAsBoolean());
                  this.afterChange.run();
               }
            )
         );
      }
   }

   private static final class ValueSlider extends AbstractSliderButton {
      private final SmartAimConfigScreen.SliderRow row;

      private ValueSlider(Box box, SmartAimConfigScreen.SliderRow row) {
         super(box.x(), box.y(), box.w(), box.h(), Component.empty(), fractionOf(row));
         this.row = row;
         this.updateMessage();
      }

      private static double fractionOf(SmartAimConfigScreen.SliderRow row) {
         double span = row.max() - row.min();
         double raw = row.getter().getAsDouble();
         return span <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, (raw - row.min()) / span));
      }

      private double current() {
         double raw = this.row.min() + this.value * (this.row.max() - this.row.min());
         double stepped = Math.round(raw / this.row.step()) * this.row.step();
         return Math.max(this.row.min(), Math.min(this.row.max(), stepped));
      }

      protected void updateMessage() {
         this.setMessage(SmartAimConfigScreen.label(this.row.key(), Component.literal(format(this.current()))));
      }

      protected void applyValue() {
         this.row.setter().accept(this.current());
      }

      private static String format(double value) {
         return value == Math.rint(value) ? String.valueOf((long)value) : String.format(Locale.ROOT, "%.1f", new Object[]{value});
      }
   }
}
