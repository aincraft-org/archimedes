package dev.mintychochip.archimedes.collision;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic exposed-block selection for a ship collision hull. */
public final class CollisionHull {
  /** Six cardinal offsets used to identify exposed blocks. */
  private static final int[][] DIRECTIONS = {
    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
  };

  private CollisionHull() {}

  /**
   * Returns every captured block with at least one unoccupied six-direction neighbor.
   *
   * @param ship ship whose exposed blocks are selected
   * @return sorted exposed block positions
   */
  public static List<BlockPos> exposedBlocks(Ship ship) {
    Set<BlockPos> occupied = new HashSet<>();
    for (ShipBlock block : ship.blocks()) {
      occupied.add(block.pos());
    }
    return ship.blocks().stream()
        .map(ShipBlock::pos)
        .filter(pos -> isExposed(pos, occupied))
        .sorted(
            Comparator.comparingInt(BlockPos::x)
                .thenComparingInt(BlockPos::y)
                .thenComparingInt(BlockPos::z))
        .toList();
  }

  /**
   * Returns every captured block whose +Y neighbor is unoccupied.
   *
   * @param ship ship whose top-bearing blocks are selected
   * @return sorted top-exposed block positions
   */
  public static List<BlockPos> topExposedBlocks(Ship ship) {
    Set<BlockPos> occupied = new HashSet<>();
    for (ShipBlock block : ship.blocks()) {
      occupied.add(block.pos());
    }
    return ship.blocks().stream()
        .map(ShipBlock::pos)
        .filter(pos -> !occupied.contains(new BlockPos(pos.x(), pos.y() + 1, pos.z())))
        .sorted(
            Comparator.comparingInt(BlockPos::x)
                .thenComparingInt(BlockPos::y)
                .thenComparingInt(BlockPos::z))
        .toList();
  }

  private static boolean isExposed(BlockPos pos, Set<BlockPos> occupied) {
    for (int[] direction : DIRECTIONS) {
      if (!occupied.contains(
          new BlockPos(pos.x() + direction[0], pos.y() + direction[1], pos.z() + direction[2]))) {
        return true;
      }
    }
    return false;
  }
}
