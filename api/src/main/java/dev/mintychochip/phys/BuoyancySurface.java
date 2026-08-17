package dev.mintychochip.phys;

/** World queries the buoyancy engine needs, separated for unit testing. */
public interface BuoyancySurface {
  /**
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return true when the position is water
   */
  boolean isWater(int x, int y, int z);

  /**
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return true when the position is clear for a ship block (air or water)
   */
  boolean isClear(int x, int y, int z);
}
