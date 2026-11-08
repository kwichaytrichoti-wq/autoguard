// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.client;

import com.smartaim.SmartAimConfig;
import com.smartaim.catalog.EntityCatalog;
import com.smartaim.catalog.TargetableTypes;
import com.smartaim.client.layout.Box;
import com.smartaim.client.layout.ListLayout;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.ObjectSelectionList.Entry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartAimEntityListScreen extends Screen {
   private static final Logger LOGGER = LoggerFactory.getLogger("smartaim/entity-list");
   private static final int COLOR_TEXT = -1;
   private static final int COLOR_DIM = -6250336;
   private static final int COLOR_ALWAYS = -11141291;
   private static final int COLOR_NEVER = -43691;
   private static final int ROW_HEIGHT = 14;
   private final SmartAimConfigScreen parent;
   private final List<EntityCatalog.Entry> catalog;
   private String query = "";
   private EditBox search;
   private SmartAimEntityListScreen.EntitySelectionList list;
   private int matchCount;

   public SmartAimEntityListScreen(SmartAimConfigScreen parent) {
      super(Component.translatable("smartaim.screen.entity_list"));
      this.parent = parent;
      this.catalog = buildCatalog();
      LOGGER.debug("Entity type list opened: {} targetable entity types", this.catalog.size());
   }

   public static int catalogSize() {
      return buildCatalog().size();
   }

   private SmartAimConfig draft() {
      return this.parent.draft();
   }

   protected void init() {
      ListLayout.Result layout = ListLayout.compute(this.width, this.height);
      Box searchBox = layout.search();
      this.search = new EditBox(this.font, searchBox.x(), searchBox.y(), searchBox.w(), searchBox.h(), Component.translatable("smartaim.search.hint"));
      this.search.setHint(Component.translatable("smartaim.search.hint"));
      this.search.setMaxLength(64);
      this.addRenderableWidget(this.search);
      Box listBox = layout.list();
      this.list = new SmartAimEntityListScreen.EntitySelectionList(this.minecraft, listBox.w(), listBox.h(), listBox.y(), 14);
      this.list.updateSizeAndPosition(listBox.w(), listBox.h(), listBox.x(), listBox.y());
      this.addRenderableWidget(this.list);
      this.search.setValue(this.query);
      this.search.setResponder(text -> {
         this.query = text;
         this.refresh();
      });
      this.refresh();
      Box done = layout.done();
      this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.closeToParent()).bounds(done.x(), done.y(), done.w(), done.h()).build());
   }

   private void refresh() {
      List<EntityCatalog.Entry> matches = EntityCatalog.filter(this.catalog, this.query);
      this.matchCount = matches.size();
      this.list.setEntries(matches);
   }

   private void closeToParent() {
      this.minecraft.gui.setScreen(this.parent);
   }

   public void onClose() {
      this.closeToParent();
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      ListLayout.Result layout = ListLayout.compute(this.width, this.height);
      Box title = layout.title();
      Box hint = layout.hint();
      graphics.centeredText(this.font, this.title, title.centreX(), title.y(), -1);
      graphics.text(this.font, this.clip(Component.translatable("smartaim.note.entity_list_hint"), hint.w()), hint.x(), hint.y(), -6250336);
      if (this.matchCount == 0) {
         Box empty = layout.empty();
         graphics.centeredText(this.font, Component.translatable("smartaim.state.no_matches"), empty.centreX(), empty.y(), -6250336);
      }
   }

   private Component clip(Component text, int width) {
      return Component.literal(this.font.plainSubstrByWidth(text.getString(), Math.max(0, width)));
   }

   private static List<EntityCatalog.Entry> buildCatalog() {
      return TargetableTypes.entries();
   }

   private final class EntityRow extends Entry<SmartAimEntityListScreen.EntityRow> {
      private final EntityCatalog.Entry entry;

      private EntityRow(EntityCatalog.Entry entry) {
         this.entry = entry;
      }

      public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
         String action = SmartAimEntityListScreen.this.draft().overrideForId(this.entry.id());
         int color = "allow".equals(action) ? -11141291 : ("deny".equals(action) ? -43691 : -1);
         int left = this.getContentX() + 4;
         int textY = this.getContentY() + Math.max(0, (this.getContentHeight() - 8) / 2);
         Component name = Component.literal(this.entry.name());
         Component state = Component.translatable(EntityCatalog.stateKey(action));
         if (hovered) {
            graphics.fill(this.getContentX(), this.getContentY(), this.getContentRight(), this.getContentBottom(), 822083583);
         }

         graphics.text(SmartAimEntityListScreen.this.font, name, left, textY, -1);
         graphics.text(SmartAimEntityListScreen.this.font, state, this.getContentRight() - SmartAimEntityListScreen.this.font.width(state) - 4, textY, color);
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
         if (!this.isMouseOver(event.x(), event.y())) {
            return false;
         }

         SmartAimEntityListScreen.this.draft()
            .setOverrideById(this.entry.id(), EntityCatalog.nextState(SmartAimEntityListScreen.this.draft().overrideForId(this.entry.id())));
         return true;
      }

      public Component getNarration() {
         return Component.literal(this.entry.name());
      }
   }

   private final class EntitySelectionList extends ObjectSelectionList<SmartAimEntityListScreen.EntityRow> {
      private EntitySelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
         super(minecraft, width, height, y, itemHeight);
         this.centerListVertically = false;
      }

      public int getRowWidth() {
         return Math.max(60, this.getWidth() - 16);
      }

      private void setEntries(List<EntityCatalog.Entry> entries) {
         this.clearEntries();

         for (EntityCatalog.Entry entry : entries) {
            this.addEntry(SmartAimEntityListScreen.this.new EntityRow(entry));
         }

         this.setScrollAmount(0.0);
      }
   }
}
