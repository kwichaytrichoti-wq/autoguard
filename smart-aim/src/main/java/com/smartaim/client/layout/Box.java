// Decompiled from smartaim-1.2.3.jar with Vineflower (JavaScript/WASM).
package com.smartaim.client.layout;

public record Box(int x, int y, int w, int h) {
   public int right() {
      return this.x + this.w;
   }

   public int bottom() {
      return this.y + this.h;
   }

   public boolean isEmpty() {
      return this.w <= 0 || this.h <= 0;
   }

   public boolean intersects(Box other) {
      return this.x < other.right() && other.x < this.right() && this.y < other.bottom() && other.y < this.bottom();
   }

   public boolean contains(Box other) {
      return other.x >= this.x && other.y >= this.y && other.right() <= this.right() && other.bottom() <= this.bottom();
   }

   public boolean inside(int screenWidth, int screenHeight) {
      return this.x >= 0 && this.y >= 0 && this.right() <= screenWidth && this.bottom() <= screenHeight;
   }

   public int centreX() {
      return this.x + this.w / 2;
   }

   public String toString() {
      return "[" + this.x + "," + this.y + " " + this.w + "x" + this.h + "]";
   }
}
