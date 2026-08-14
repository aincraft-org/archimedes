package dev.jlo.ships.deck;

import dev.jlo.ships.model.Ship;
import java.util.Collection;
import java.util.Set;

/**
 * Contract for resolving which absolute deck positions need a support block
 * and validating that they are unobstructed. Separated from Bukkit so the
 * walkability rules are unit-testable.
 */
public interface DeckSurface {
  /** Returns true when the world permits blocking in the given position. */
  boolean canPlace(int x, int y, int z);

  /** Returns true when the position is currently air or support-permitted. */
  boolean isClear(int x, int y, int z);

  /** Sets a barrier support block at the position, returning success. */
  boolean placeBarrier(int x, int y, int z);

  /** Removes a barrier support block at the position. */
  void removeBarrier(int x, int y, int z);

  /**
   * Walks the ship model and computes every absolute position, in
   * deterministic order, that needs a support: one above each block whose
   * upper neighbor is neither a ship block nor already a support.
   */
  static Set<long[]> supportPositions(Ship ship) {
    return SupportResolver.resolve(ship);
  }

  /** Handles deployment and cleanup of deck support blocks. */
  final class SupportResolver {
    private SupportResolver() {}

    /** Returns absolute support positions as packed long keys. */
    public static Set<long[]> resolve(Ship ship) {
      Set<long[]> supports = new java.util.LinkedHashSet<>();
      Set<String> occupied = new java.util.HashSet<>();
      for (var block : ship.blocks()) {
        int ax = ship.origin().x() + block.pos().x();
        int ay = ship.origin().y() + block.pos().y();
        int az = ship.origin().z() + block.pos().z();
        occupied.add(ax + "," + ay + "," + az);
      }
      for (var block : ship.blocks()) {
        int ax = ship.origin().x() + block.pos().x();
        int ay = ship.origin().y() + block.pos().y();
        int az = ship.origin().z() + block.pos().z();
        String above = ax + "," + (ay + 1) + "," + az;
        if (!occupied.contains(above)) {
          supports.add(new long[] {ax, ay + 1, az});
        }
      }
      return supports;
    }
  }
}