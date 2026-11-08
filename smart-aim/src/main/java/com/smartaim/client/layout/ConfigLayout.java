// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.client.layout;

import java.util.ArrayList;
import java.util.List;

public final class ConfigLayout {
   public static final int PAD = 4;
   public static final int GAP = 2;
   public static final int LINE = 9;
   public static final int BUTTON = 14;
   public static final int ROW = 15;
   public static final int SCROLL_COLUMN = 40;
   public static final int MAX_HELP_LINES = 3;

   private ConfigLayout() {
   }

   public static ConfigLayout.Result compute(int width, int height, ConfigLayout.Options options) {
      int left = 4;
      int innerWidth = Math.max(0, width - 8);
      int y = 4;
      Box title = new Box(left, y, innerWidth, 9);
      y = title.bottom() + 2;
      Box notice = null;
      if (options.showNotice()) {
         notice = new Box(left, y, innerWidth, 9);
         y = notice.bottom() + 2;
      }

      int tabWidth = Math.max(0, (innerWidth - 4) / 3);
      List<Box> tabs = List.of(
         new Box(left, y, tabWidth, 14), new Box(left + tabWidth + 2, y, tabWidth, 14), new Box(left + 2 * (tabWidth + 2), y, tabWidth, 14)
      );
      y = ((Box)tabs.get(0)).bottom() + 2;
      int footerY = Math.max(y, height - 4 - 14);
      int buttonWidth = Math.max(0, (innerWidth - 4) / 3);
      Box reset = new Box(left, footerY, buttonWidth, 14);
      Box cancel = new Box(left + buttonWidth + 2, footerY, buttonWidth, 14);
      Box save = new Box(left + 2 * (buttonWidth + 2), footerY, buttonWidth, 14);
      int helpLines = options.helpLines();
      int helpHeight = helpLines > 0 ? helpLines * 9 + 2 : 0;
      int contentBottom = footerY - 2 - helpHeight;
      Box help = helpLines > 0 ? new Box(left, contentBottom + 2, innerWidth, helpLines * 9) : null;
      Box viewport = new Box(left, y, innerWidth, Math.max(0, contentBottom - y));
      int columns = options.columns();
      int rowCount = options.rowCount();
      int linesAvailable = viewport.h() / 17;
      int totalLines = (rowCount + columns - 1) / columns;
      int maxScroll = Math.max(0, totalLines - Math.max(0, linesAvailable));
      int scroll = Math.min(options.scroll(), maxScroll);
      boolean needsScroll = maxScroll > 0;
      int rowAreaWidth = innerWidth;
      Box scrollUp = null;
      Box scrollDown = null;
      if (needsScroll) {
         rowAreaWidth = Math.max(0, innerWidth - 40 - 2);
         int scrollX = left + rowAreaWidth + 2;
         if (viewport.h() >= 30) {
            scrollUp = new Box(scrollX, viewport.y(), 40, 14);
            scrollDown = new Box(scrollX, viewport.bottom() - 14, 40, 14);
         } else {
            int halfWidth = Math.max(0, 19);
            scrollUp = new Box(scrollX, viewport.y(), halfWidth, 14);
            scrollDown = new Box(scrollX + halfWidth + 2, viewport.y(), halfWidth, 14);
         }
      }

      int columnWidth = Math.max(0, (rowAreaWidth - (columns - 1) * 2) / columns);
      List<ConfigLayout.RowSlot> rows = new ArrayList();

      for (int line = 0; line < Math.max(0, linesAvailable); line++) {
         int sourceLine = scroll + line;
         if (sourceLine >= totalLines) {
            break;
         }

         int rowY = viewport.y() + line * 17;

         for (int column = 0; column < columns; column++) {
            int index = sourceLine * columns + column;
            if (index < rowCount) {
               int rowX = left + column * (columnWidth + 2);
               rows.add(new ConfigLayout.RowSlot(index, new Box(rowX, rowY, columnWidth, 15)));
               continue;
            }
         }
      }

      return new ConfigLayout.Result(title, notice, tabs, viewport, List.copyOf(rows), scrollUp, scrollDown, maxScroll, scroll, help, reset, cancel, save);
   }

   public record Options(boolean showNotice, int helpLines, int rowCount, int columns, int scroll) {
      public Options {
         helpLines = Math.max(0, Math.min(3, helpLines));
         rowCount = Math.max(0, rowCount);
         columns = Math.max(1, columns);
         scroll = Math.max(0, scroll);
      }
   }

   public record Result(
      Box title,
      Box notice,
      List<Box> tabs,
      Box viewport,
      List<ConfigLayout.RowSlot> rows,
      Box scrollUp,
      Box scrollDown,
      int maxScroll,
      int scroll,
      Box help,
      Box reset,
      Box cancel,
      Box save
   ) {
      public List<Box> leaves() {
         List<Box> boxes = new ArrayList();
         boxes.add(this.title);
         if (this.notice != null) {
            boxes.add(this.notice);
         }

         boxes.addAll(this.tabs);
         if (this.help != null) {
            boxes.add(this.help);
         }

         if (this.scrollUp != null) {
            boxes.add(this.scrollUp);
         }

         if (this.scrollDown != null) {
            boxes.add(this.scrollDown);
         }

         for (ConfigLayout.RowSlot slot : this.rows) {
            boxes.add(slot.box());
         }

         boxes.add(this.reset);
         boxes.add(this.cancel);
         boxes.add(this.save);
         return boxes;
      }
   }

   public record RowSlot(int index, Box box) {
   }
}
