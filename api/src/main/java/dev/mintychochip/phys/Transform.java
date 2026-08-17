package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

public record Transform(Vector3dc position, Quaterniondc orientation) {
  public Transform {
    Objects.requireNonNull(position);
    Objects.requireNonNull(orientation);
    Vectors.requireFinite(position);
    Quaternions.requireNormalized(orientation);
  }
}
