package dev.jlo.ships.buoyancy;

import dev.jlo.ships.model.Ship;

/** Pure waterline and submerged-volume resolution from the ship model. */
public final class BuoyancyResolver {
  /** Sentinel for a column with no water. */
  public static final int NO_WATER = Integer.MIN_VALUE;

  private BuoyancyResolver() {}

  /**
   * Returns the lowest water surface y over all ship columns, or {@link #NO_WATER} when no column
   * has water under the hull.
   *
   * @param ship the ship to inspect
   * @param surface the world surface
   * @return the effective water surface y
   */
  public static int waterSurfaceY(Ship ship, BuoyancySurface surface) {
    int min = Integer.MAX_VALUE;
    for (var block : ship.blocks()) {
      int ax = ship.origin().x() + block.pos().x();
      int az = ship.origin().z() + block.pos().z();
      int bottom = ship.origin().y() + ship.pose().anchorDy() + block.pos().y();
      int column = columnWaterSurface(surface, ax, bottom, az);
      if (column != NO_WATER && column < min) {
        min = column;
      }
    }
    return min == Integer.MAX_VALUE ? NO_WATER : min;
  }

  /**
   * Returns the number of ship blocks at or below the water surface.
   *
   * @param ship the ship to inspect
   * @param surface the world surface
   * @return the submerged block count
   */
  public static int submergedVolume(Ship ship, BuoyancySurface surface) {
    int count = 0;
    for (var block : ship.blocks()) {
      int ax = ship.origin().x() + block.pos().x();
      int az = ship.origin().z() + block.pos().z();
      int blockY = ship.origin().y() + ship.pose().anchorDy() + block.pos().y();
      int columnSurface = columnWaterSurface(surface, ax, blockY, az);
      if (columnSurface != NO_WATER && blockY <= columnSurface) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns the pose y that places the hull bottom at the water surface.
   *
   * @param ship the ship to inspect
   * @param surface the world surface
   * @return the equilibrium pose y, or 0 when no water
   */
  public static double equilibriumY(Ship ship, BuoyancySurface surface) {
    int surfaceY = waterSurfaceY(ship, surface);
    if (surfaceY == NO_WATER) {
      return 0;
    }
    int minRelY = Integer.MAX_VALUE;
    for (var block : ship.blocks()) {
      if (block.pos().y() < minRelY) {
        minRelY = block.pos().y();
      }
    }
    return surfaceY - ship.origin().y() - minRelY;
  }

  private static int columnWaterSurface(BuoyancySurface surface, int x, int bottom, int z) {
    boolean sealed = false;
    int highest = NO_WATER;
    for (int y = bottom + 64; y >= bottom - 64; y--) {
      if (surface.isWater(x, y, z)) {
        if (!sealed && highest == NO_WATER) {
          highest = y;
        }
      } else if (!surface.isClear(x, y, z)) {
        sealed = true;
      }
    }
    return highest;
  }
}
