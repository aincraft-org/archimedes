package dev.mintychochip.phys;

import org.joml.Quaterniondc;

/** Validation helpers for JOML quaternion values used by the physics API. */
public final class Quaternions {
  private Quaternions() {}

  /**
   * Requires a finite unit quaternion within a {@code 1e-9} length tolerance.
   *
   * @param q quaternion to validate
   * @throws IllegalArgumentException if the quaternion is non-finite or not normalized
   */
  public static void requireNormalized(Quaterniondc q) {
    if (!q.isFinite()) throw new IllegalArgumentException("quaternion must be finite");
    double length = Math.sqrt(q.lengthSquared());
    if (Math.abs(length - 1.0) > 1e-9) {
      throw new IllegalArgumentException("quaternion must be normalized");
    }
  }
}
