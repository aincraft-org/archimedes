package dev.mintychochip.archimedes.model;

/** Fractional vertical offset of a ship from its build-site origin. */
public final class ShipPose {
  /** Fractional vertical offset. */
  private final double y;

  /**
   * Creates a pose.
   *
   * @param y the fractional vertical offset
   */
  public ShipPose(double y) {
    this.y = y;
  }

  /**
   * @return the fractional vertical offset
   */
  public double y() {
    return y;
  }

  /**
   * @return the integer collision anchor (floor of y)
   */
  public int anchorDy() {
    return (int) Math.floor(y);
  }
}
