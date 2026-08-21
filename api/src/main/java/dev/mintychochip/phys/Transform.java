package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
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

  /**
   * Applies this parent transform to a local child pose.
   *
   * <p>World position is {@code this.position + R * local.position}; world orientation is the
   * product of this orientation and {@code local}'s orientation.
   *
   * @param local pose expressed in this transform's local frame
   * @return composed world-space transform
   * @throws NullPointerException if {@code local} is {@code null}
   */
  public Transform compose(Transform local) {
    Objects.requireNonNull(local);
    Vector3d p = orientation().transform(local.position(), new Vector3d()).add(position());
    Quaterniond q = new Quaterniond(orientation()).mul(local.orientation());
    return new Transform(p, q);
  }
}
