package dev.jlo.ships.scan;

import org.bukkit.World;

/** World reads the ship scanner needs, separated for unit testing. */
public interface ScannerWorld {
  /**
   * Returns the material registry name at a position.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return the material registry name
   */
  String materialAt(int x, int y, int z);

  /**
   * Returns whether the block at a position is air.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return true when the block is air
   */
  boolean airAt(int x, int y, int z);

  /**
   * Wraps a Bukkit world.
   *
   * @param world the Bukkit world
   * @return a scanner world over the world
   */
  static ScannerWorld of(World world) {
    return new ScannerWorld() {
      @Override
      public String materialAt(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().getKey().toString();
      }

      @Override
      public boolean airAt(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isAir();
      }
    };
  }
}
