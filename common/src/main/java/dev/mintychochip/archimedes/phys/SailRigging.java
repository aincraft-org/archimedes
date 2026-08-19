package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.sail.SailMesh;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How well a cloth cell is supported by rigid hull. Distance is the shortest 6-direction path
 * through intact cloth to a non-cloth block. Torn cells are gaps. Unsupported cloth (no rigid
 * neighbor anywhere) is {@link Integer#MAX_VALUE}. Strength is {@code breakLoad / (1 + distance)}.
 */
public final class SailRigging {
  /** Default load a mast-adjacent cell can take. Default plugin wind is below this. */
  public static final double DEFAULT_BREAK_LOAD = 100.0;

  /** Face-adjacent offsets used to walk cloth to hull. */
  private static final int[][] NEIGHBORS = {
    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
  };

  private SailRigging() {}

  /**
   * Graph distance through cloth from {@code cell} to the nearest rigid hull block.
   *
   * @param ship vehicle whose blocks form the graph
   * @param cell cloth cell
   * @return {@code 0} if adjacent to rigid, {@link Integer#MAX_VALUE} if no rigid exists
   */
  public static int distanceToRigid(Vehicle ship, BlockPos cell) {
    Objects.requireNonNull(ship);
    Objects.requireNonNull(cell);
    Map<BlockPos, String> data = index(ship);
    if (!data.containsKey(cell)) {
      return Integer.MAX_VALUE;
    }
    Map<BlockPos, Integer> dist = new HashMap<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    dist.put(cell, 0);
    queue.add(cell);
    int best = Integer.MAX_VALUE;
    while (!queue.isEmpty()) {
      BlockPos here = queue.removeFirst();
      int d = dist.get(here);
      if (d >= best) {
        continue;
      }
      for (int[] n : NEIGHBORS) {
        BlockPos next = new BlockPos(here.x() + n[0], here.y() + n[1], here.z() + n[2]);
        String appearance = data.get(next);
        if (appearance == null || ship.isTorn(next)) {
          continue;
        }
        if (!SailMesh.isCloth(appearance)) {
          best = Math.min(best, d);
          continue;
        }
        if (!dist.containsKey(next)) {
          dist.put(next, d + 1);
          queue.add(next);
        }
      }
    }
    return best;
  }

  /**
   * Whether a cloth cell's aerodynamic load exceeds its support.
   *
   * @param load force magnitude on the cell
   * @param distance {@link #distanceToRigid}
   * @param breakLoad mast-adjacent capacity
   * @return whether the cell tears
   */
  public static boolean fails(double load, int distance, double breakLoad) {
    return fails(load, distance, breakLoad, true);
  }

  /**
   * Whether a cloth cell's aerodynamic load exceeds its support.
   *
   * @param load force magnitude on the cell
   * @param distance {@link #distanceToRigid}
   * @param breakLoad mast-adjacent capacity
   * @param hullPresent whether the vehicle has any non-cloth block
   * @return whether the cell tears
   */
  public static boolean fails(double load, int distance, double breakLoad, boolean hullPresent) {
    if (distance == Integer.MAX_VALUE) {
      return hullPresent && load > 0;
    }
    double strength = breakLoad / (1.0 + distance);
    return load > strength;
  }

  /**
   * @param ship vehicle whose captured blocks are inspected
   * @return whether any captured block is rigid hull
   */
  public static boolean hasRigid(Vehicle ship) {
    Objects.requireNonNull(ship);
    for (ShipBlock block : ship.blocks()) {
      if (!SailMesh.isCloth(block.blockData())) {
        return true;
      }
    }
    return false;
  }

  private static Map<BlockPos, String> index(Vehicle ship) {
    Map<BlockPos, String> data = new HashMap<>();
    for (ShipBlock block : ship.blocks()) {
      data.put(block.pos(), block.blockData());
    }
    return data;
  }
}
