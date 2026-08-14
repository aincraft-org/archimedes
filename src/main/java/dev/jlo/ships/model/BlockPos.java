package dev.jlo.ships.model;

/** Axis-aligned integer position of a ship block relative to the ship origin. */
public final class BlockPos {
  /** Relative x coordinate. */
  private final int x;

  /** Relative y coordinate. */
  private final int y;

  /** Relative z coordinate. */
  private final int z;

  /**
   * Creates a relative block position.
   *
   * @param x the relative x coordinate
   * @param y the relative y coordinate
   * @param z the relative z coordinate
   */
  public BlockPos(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * @return the relative x coordinate
   */
  public int x() {
    return x;
  }

  /**
   * @return the relative y coordinate
   */
  public int y() {
    return y;
  }

  /**
   * @return the relative z coordinate
   */
  public int z() {
    return z;
  }
}
