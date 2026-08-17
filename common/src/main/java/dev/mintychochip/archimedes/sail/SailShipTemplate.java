package dev.mintychochip.archimedes.sail;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import java.util.ArrayList;
import java.util.List;

/**
 * Predetermined demo sail: a 3×3 oak deck, a 4-high mast, and a 3×3 wool sheet on +Z.
 *
 * <p>Used by {@code /ship sail}. Geometry is fixed; callers choose only the world origin.
 */
public final class SailShipTemplate {
  /** Deck and sail width (odd, centered on the mast). */
  public static final int SPAN = 3;

  /** Wool sheet height in blocks. */
  public static final int SAIL_HEIGHT = 3;

  /** Mast height in blocks above the deck. */
  public static final int MAST_HEIGHT = 4;

  /** Deck appearance. */
  public static final String DECK = "minecraft:oak_planks";

  /** Mast appearance. */
  public static final String MAST = "minecraft:oak_log";

  /** Sail appearance (tessellated by {@link SailMesh}). */
  public static final String SAIL = "minecraft:white_wool";

  private SailShipTemplate() {}

  /**
   * @return the fixed relative block list: 9 deck, 4 mast, 9 sail
   */
  public static List<ShipBlock> blocks() {
    int half = SPAN / 2;
    List<ShipBlock> blocks = new ArrayList<>(22);
    for (int x = -half; x <= half; x++) {
      for (int z = -half; z <= half; z++) {
        blocks.add(new ShipBlock(new BlockPos(x, 0, z), DECK));
      }
    }
    for (int y = 1; y <= MAST_HEIGHT; y++) {
      blocks.add(new ShipBlock(new BlockPos(0, y, 0), MAST));
    }
    int sailMinY = MAST_HEIGHT - SAIL_HEIGHT + 1;
    for (int x = -half; x <= half; x++) {
      for (int y = sailMinY; y <= MAST_HEIGHT; y++) {
        blocks.add(new ShipBlock(new BlockPos(x, y, 1), SAIL));
      }
    }
    return List.copyOf(blocks);
  }
}
