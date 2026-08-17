package dev.mintychochip.phys;

import org.joml.Vector3dc;

/** Axis-aligned bounds represented by minimum and maximum world coordinates. */
public interface Bounds {
  /**
   * Returns the minimum coordinate on each axis.
   *
   * @return minimum coordinates
   */
  Vector3dc min();

  /**
   * Returns the maximum coordinate on each axis.
   *
   * @return maximum coordinates
   */
  Vector3dc max();

  /**
   * Returns the enclosed axis-aligned volume.
   *
   * @return volume
   */
  double volume();

  /**
   * Reports whether a point lies inside these bounds.
   *
   * @param point point to test
   * @return whether the point is contained
   */
  boolean contains(Vector3dc point);

  /**
   * Reports whether this volume overlaps {@code other} on all three axes.
   *
   * @param other bounds to test
   * @return whether the boxes overlap or touch
   */
  default boolean overlaps(Bounds other) {
    return min().x() <= other.max().x()
        && max().x() >= other.min().x()
        && min().y() <= other.max().y()
        && max().y() >= other.min().y()
        && min().z() <= other.max().z()
        && max().z() >= other.min().z();
  }
}
