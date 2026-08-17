package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Infinite supporting plane. Contact is {@code (x − point) · n ≤ slop}. The gravity-derived normal
 * load is {@code max(0, −m g · n)} while in contact.
 */
public final class ContactPlane {
  /** A point on the plane. */
  private final Vector3d point;

  /** Unit outward normal (points toward the free side). */
  private final Vector3d normal;

  /** Distance tolerance for “on the plane”. */
  private final double slop;

  public ContactPlane(Vector3dc point, Vector3dc normal) {
    this(point, normal, 1e-6);
  }

  /**
   * @param point a point on the plane
   * @param normal free-side normal (normalized on store)
   * @param slop non-negative contact tolerance
   */
  public ContactPlane(Vector3dc point, Vector3dc normal, double slop) {
    Objects.requireNonNull(point);
    Objects.requireNonNull(normal);
    Vectors.requireFinite(point);
    Vectors.requireFinite(normal);
    if (normal.lengthSquared() == 0) {
      throw new IllegalArgumentException("normal must be non-zero");
    }
    if (!Double.isFinite(slop) || slop < 0) {
      throw new IllegalArgumentException("slop must be finite and non-negative");
    }
    this.point = new Vector3d(point);
    this.normal = new Vector3d(normal).normalize();
    this.slop = slop;
  }

  public Vector3dc point() {
    return point;
  }

  public Vector3dc normal() {
    return normal;
  }

  public boolean contacting(Vector3dc position) {
    Objects.requireNonNull(position);
    return new Vector3d(position).sub(point).dot(normal) <= slop;
  }

  /**
   * Compressive gravity load along {@link #normal()} while in contact; 0 otherwise.
   *
   * @param body body whose mass and position are sampled
   * @param world supplies gravity
   * @return non-negative normal load
   */
  public double gravityLoad(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    if (!contacting(body.transform().position())) {
      return 0;
    }
    double compressive = -new Vector3d(world.gravity()).dot(normal) * body.mass();
    return Math.max(0, compressive);
  }
}
