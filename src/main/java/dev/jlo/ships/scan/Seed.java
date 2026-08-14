package dev.jlo.ships.scan;

/** Origin block for a ship assembly scan. */
public final class Seed {
  private final int x;
  private final int y;
  private final int z;

  /** Creates a scan seed. */
  public Seed(int x, int y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /** Returns the seed x coordinate. */
  public int x() {
    return x;
  }

  /** Returns the seed y coordinate. */
  public int y() {
    return y;
  }

  /** Returns the seed z coordinate. */
  public int z() {
    return z;
  }
}