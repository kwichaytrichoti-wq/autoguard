// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.client.layout;

public final class ListLayout {
   private ListLayout() {
   }

   public static ListLayout.Result compute(int width, int height) {
      int left = 4;
      int innerWidth = Math.max(0, width - 8);
      int y = 4;
      Box title = new Box(left, y, innerWidth, 9);
      y = title.bottom() + 2;
      Box hint = new Box(left, y, innerWidth, 9);
      y = hint.bottom() + 2;
      Box search = new Box(left, y, innerWidth, 14);
      y = search.bottom() + 2;
      int doneY = height - 4 - 14;
      Box done = new Box(left, doneY, innerWidth, 14);
      Box list = new Box(left, y, innerWidth, Math.max(0, done.y() - 2 - y));
      Box empty = new Box(left, list.y() + Math.max(0, (list.h() - 9) / 2), innerWidth, 9);
      return new ListLayout.Result(title, hint, search, list, empty, done);
   }

   public record Result(Box title, Box hint, Box search, Box list, Box empty, Box done) {
   }
}
