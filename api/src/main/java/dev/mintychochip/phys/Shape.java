package dev.mintychochip.phys;

/** Geometric shape that can compute transformed bounds and volume. */
public interface Shape {
  /**
   * Returns this shape's world-space bounds under a transform.
   *
   * @param transform world-space shape transform
   * @return transformed bounds
   */
  Bounds bounds(Transform transform);

  /**
   * Returns this shape's volume.
   *
   * @return shape volume
   */
  double volume();
}
