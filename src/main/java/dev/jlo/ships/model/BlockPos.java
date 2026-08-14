package dev.jlo.ships.model;

/** Axis-aligned integer position of a ship block relative to the ship origin. */
public final class BlockPos {
  private final int x;
  private final int y;
  private final int z;

  /** Creates a relative block position. */
  public BlockPos(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /** Returns the relative x coordinate. */
  public int x() {
    return x;
  }

  /** Returns the relative y coordinate. */
  public int y() {
    return y;
  }

  /** Returns the relative z coordinate. */
  public int z() {
    return z;
  }
}