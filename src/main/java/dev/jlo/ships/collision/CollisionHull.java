package dev.jlo.ships.collision;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic exposed-block selection for a ship collision hull. */
public final class CollisionHull {
  private static final int[][] DIRECTIONS = {
    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
  };

  private CollisionHull() {}

  /** Returns every captured block with at least one unoccupied six-direction neighbor. */
  public static List<BlockPos> exposedBlocks(Ship ship) {
    Set<BlockPos> occupied = new HashSet<>();
    for (ShipBlock block : ship.blocks()) {
      occupied.add(block.pos());
    }
    return ship.blocks().stream()
        .map(ShipBlock::pos)
        .filter(pos -> isExposed(pos, occupied))
        .sorted(Comparator.comparingInt(BlockPos::x)
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
