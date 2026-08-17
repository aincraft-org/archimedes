package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

/**
 * World-space position and orientation of a rigid body.
 *
 * @param position world-space position with finite components
 * @param orientation unit orientation quaternion
 */
public record Transform(Vector3dc position, Quaterniondc orientation) {
  /**
   * Creates a transform after validating its position and orientation.
   *
   * @param position world-space position with finite components
   * @param orientation unit orientation quaternion
   * @throws NullPointerException if either component is {@code null}
   * @throws IllegalArgumentException if position is non-finite or orientation is not normalized
   */
  public Transform {
    Objects.requireNonNull(position);
    Objects.requireNonNull(orientation);
    Vectors.requireFinite(position);
    Quaternions.requireNormalized(orientation);
  }
}
