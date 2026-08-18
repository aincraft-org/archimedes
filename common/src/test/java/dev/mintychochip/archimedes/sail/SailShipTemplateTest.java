package dev.mintychochip.archimedes.sail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.ShipBlock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Predetermined demo sail used by {@code /arch sail}. */
class SailShipTemplateTest {
  private static final String OAK_PLANKS = "minecraft:oak_planks";
  private static final String OAK_LOG = "minecraft:oak_log";
  private static final String WHITE_WOOL = "minecraft:white_wool";

  @Test
  void smallIsAThreeByThreeSail() {
    List<ShipBlock> blocks = SailShipTemplate.blocks(SailShipTemplate.Size.SMALL);

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
  void defaultAndLargerSizesGrowTheSail() {
    int small = count(SailShipTemplate.blocks(SailShipTemplate.Size.SMALL), WHITE_WOOL);
    int medium = count(SailShipTemplate.blocks(), WHITE_WOOL);
    int large = count(SailShipTemplate.blocks(SailShipTemplate.Size.LARGE), WHITE_WOOL);
    assertEquals(9, small);
    assertEquals(25, medium);
    assertEquals(49, large);
    assertTrue(medium > small);
    assertTrue(large > medium);
  }

  @Test
  void parseAcceptsNamedSizesAndRejectsUnknown() {
    assertEquals(SailShipTemplate.Size.SMALL, SailShipTemplate.Size.parse("small"));
    assertEquals(SailShipTemplate.Size.MEDIUM, SailShipTemplate.Size.parse("MEDIUM"));
    assertEquals(SailShipTemplate.Size.LARGE, SailShipTemplate.Size.parse(" Large "));
    assertEquals(null, SailShipTemplate.Size.parse("huge"));
    assertEquals(null, SailShipTemplate.Size.parse(null));
  }

  @Test
  void clothIsAThreeByThreeWall() {
    Set<String> wool = new HashSet<>();
    for (ShipBlock block : SailShipTemplate.blocks(SailShipTemplate.Size.SMALL)) {
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
