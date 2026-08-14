package dev.jlo.ships.scan;

/** Origin block for a ship assembly scan. */
public final class Seed {
  /** Seed x coordinate. */
  private final int x;

  /** Seed y coordinate. */
  private final int y;

  /** Seed z coordinate. */
  private final int z;

  /**
   * Creates a scan seed.
   *
   * @param x the seed x coordinate
   * @param y the seed y coordinate
   * @param z the seed z coordinate
   */
  public Seed(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * @return the seed x coordinate
   */
  public int x() {
    return x;
  }

  /**
   * @return the seed y coordinate
   */
  public int y() {
    return y;
  }

  /**
   * @return the seed z coordinate
   */
  public int z() {
    return z;
  }
}
