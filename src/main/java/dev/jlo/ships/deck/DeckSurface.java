package dev.jlo.ships.deck;

import dev.jlo.ships.model.Ship;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Contract for resolving which absolute deck positions need a support block and validating that
 * they are unobstructed. Separated from Bukkit so the walkability rules are unit-testable.
 */
public interface DeckSurface {
  /**
   * @param x the x coordinate to check
   * @param y the y coordinate to check
   * @param z the z coordinate to check
   * @return true when the world permits blocking in the given position
   */
  boolean canPlace(int x, int y, int z);

  /**
   * @param x the x coordinate to check
   * @param y the y coordinate to check
   * @param z the z coordinate to check
   * @return true when the position is currently air
   */
  boolean isClear(int x, int y, int z);

  /**
   * @param x the x coordinate to set
   * @param y the y coordinate to set
   * @param z the z coordinate to set
   * @return true when the barrier was placed
   */
  boolean placeBarrier(int x, int y, int z);

  /**
   * @param x the x coordinate to set
   * @param y the y coordinate to set
   * @param z the z coordinate to set
   */
  void removeBarrier(int x, int y, int z);

  /**
   * Returns every absolute support position for the ship, in deterministic order, in a form this
   * surface can consume.
   *
   * @param ship the ship to inspect
   * @return the absolute support positions
   */
  default Collection<long[]> supportsFor(Ship ship) {
    return SupportResolver.resolve(ship);
  }

  /**
   * Walks the ship model and computes every absolute position that needs a support: one above each
   * block whose upper neighbor is neither a ship block nor already a support.
   *
   * @param ship the ship to inspect
   * @return the absolute support positions
   */
  static Set<long[]> supportPositions(Ship ship) {
    return SupportResolver.resolve(ship);
  }

  /** Handles deployment and cleanup of deck support blocks. */
  final class SupportResolver {
    private SupportResolver() {}

    /**
     * Returns absolute support positions.
     *
     * @param ship the ship to inspect
     * @return the absolute support positions
     */
    public static Set<long[]> resolve(Ship ship) {
      Set<long[]> supports = new LinkedHashSet<>();
      Set<String> occupied = new HashSet<>();
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
