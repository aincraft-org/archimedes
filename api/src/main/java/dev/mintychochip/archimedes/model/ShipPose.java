package dev.mintychochip.archimedes.model;

/** Fractional offset of a ship from its build-site origin. */
public final class ShipPose {
  /** Fractional east-west offset. */
  private final double x;

  /** Fractional vertical offset. */
  private final double y;

  /** Fractional north-south offset. */
  private final double z;

  /**
   * Creates a vertical-only pose (horizontal offsets are zero).
   *
   * @param y the fractional vertical offset
   */
  public ShipPose(double y) {
    this(0.0, y, 0.0);
  }

  /**
   * Creates a pose.
   *
   * @param x the fractional east-west offset
   * @param y the fractional vertical offset
   * @param z the fractional north-south offset
   */
  public ShipPose(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * @return the fractional east-west offset
   */
  public double x() {
    return x;
  }

  /**
   * @return the fractional vertical offset
   */
  public double y() {
    return y;
  }

  /**
   * @return the fractional north-south offset
   */
  public double z() {
    return z;
  }

  /**
   * @return the integer collision anchor (floor of x)
   */
  public int anchorDx() {
    return (int) Math.floor(x);
  }

  /**
   * @return the integer collision anchor (floor of y)
   */
  public int anchorDy() {
    return (int) Math.floor(y);
  }

  /**
   * @return the integer collision anchor (floor of z)
   */
  public int anchorDz() {
    return (int) Math.floor(z);
  }
}
