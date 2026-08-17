package dev.mintychochip.archimedes.sail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.ShipBlock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Predetermined demo sail used by {@code /ship sail}. */
class SailShipTemplateTest {
  private static final String OAK_PLANKS = "minecraft:oak_planks";
  private static final String OAK_LOG = "minecraft:oak_log";
  private static final String WHITE_WOOL = "minecraft:white_wool";

  @Test
  void buildsAFixedDeckMastAndThreeByThreeSail() {
    List<ShipBlock> blocks = SailShipTemplate.blocks();

    assertEquals(22, blocks.size());
    assertEquals(9, count(blocks, OAK_PLANKS));
    assertEquals(4, count(blocks, OAK_LOG));
    assertEquals(9, count(blocks, WHITE_WOOL));
    assertTrue(has(blocks, 0, 0, 0, OAK_PLANKS));
    assertTrue(has(blocks, 0, 4, 0, OAK_LOG));
    assertTrue(has(blocks, -1, 2, 1, WHITE_WOOL));
    assertTrue(has(blocks, 1, 4, 1, WHITE_WOOL));
  }

  @Test
  void clothIsAThreeByThreeWall() {
    Set<String> wool = new HashSet<>();
    for (ShipBlock block : SailShipTemplate.blocks()) {
      if (WHITE_WOOL.equals(block.blockData())) {
        wool.add(block.pos().x() + "," + block.pos().y() + "," + block.pos().z());
      }
    }
    assertEquals(9, wool.size());
    for (int x = -1; x <= 1; x++) {
      for (int y = 2; y <= 4; y++) {
        assertTrue(wool.contains(x + "," + y + ",1"), "missing sail cell " + x + "," + y);
      }
    }
  }

  private static int count(List<ShipBlock> blocks, String data) {
    int n = 0;
    for (ShipBlock block : blocks) {
      if (data.equals(block.blockData())) {
        n++;
      }
    }
    return n;
  }

  private static boolean has(List<ShipBlock> blocks, int x, int y, int z, String data) {
    for (ShipBlock block : blocks) {
      if (block.pos().x() == x
          && block.pos().y() == y
          && block.pos().z() == z
          && data.equals(block.blockData())) {
        return true;
      }
    }
    return false;
  }
}
