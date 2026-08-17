package dev.mintychochip.phys;

import org.joml.Vector3dc;

/** Validation helpers for JOML vector values used by the physics API. */
public final class Vectors {
  private Vectors() {}

  /**
   * Requires every component of a vector to be finite.
   *
   * @param v vector to validate
   * @throws IllegalArgumentException if any component is not finite
   */
  public static void requireFinite(Vector3dc v) {
    if (!Double.isFinite(v.x()) || !Double.isFinite(v.y()) || !Double.isFinite(v.z())) {
      throw new IllegalArgumentException("vector components must be finite");
    }
  }
}
