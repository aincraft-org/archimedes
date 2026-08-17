package dev.mintychochip.phys;

import org.joml.Quaterniondc;

public final class Quaternions {
  private Quaternions() {}

  public static void requireNormalized(Quaterniondc q) {
    if (!q.isFinite()) throw new IllegalArgumentException("quaternion must be finite");
    double length = Math.sqrt(q.lengthSquared());
    if (Math.abs(length - 1.0) > 1e-9) {
      throw new IllegalArgumentException("quaternion must be normalized");
    }
  }
}
