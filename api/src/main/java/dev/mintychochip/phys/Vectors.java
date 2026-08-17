package dev.mintychochip.phys;

import org.joml.Vector3dc;

public final class Vectors {
  private Vectors() {}

  public static void requireFinite(Vector3dc v) {
    if (!Double.isFinite(v.x()) || !Double.isFinite(v.y()) || !Double.isFinite(v.z())) {
      throw new IllegalArgumentException("vector components must be finite");
    }
  }
}
