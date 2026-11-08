package com.smartaim.client.layout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigLayoutTest {
   @Test void generalTrackingControlsFitOrScrollWithoutOverlap() {
      for (int[] screen : List.of(new int[]{320, 240}, new int[]{320, 120}, new int[]{854, 480})) {
         ConfigLayout.Result layout = ConfigLayout.compute(screen[0], screen[1],
            new ConfigLayout.Options(false, 0, 11, 2, 0));
         List<Box> boxes = layout.leaves();
         for (int i = 0; i < boxes.size(); i++) {
            assertTrue(boxes.get(i).inside(screen[0], screen[1]), "out of bounds: " + boxes.get(i));
            for (int j = i + 1; j < boxes.size(); j++) {
               assertFalse(boxes.get(i).intersects(boxes.get(j)),
                  "overlap: " + boxes.get(i) + " and " + boxes.get(j));
            }
         }
      }
   }
}
