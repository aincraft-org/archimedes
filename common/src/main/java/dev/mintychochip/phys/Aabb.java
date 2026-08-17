package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Axis-aligned box shape and bounds. */
public final class Aabb implements Shape, Bounds {
  /** Box center. */
  private final Vector3d center;

  /** Non-negative half extents. */
  private final Vector3d halfExtents;

  public Aabb(Vector3dc center, Vector3dc halfExtents) {
    this.center = new Vector3d(Objects.requireNonNull(center));
    this.halfExtents = new Vector3d(Objects.requireNonNull(halfExtents));
    Vectors.requireFinite(this.center);
    Vectors.requireFinite(this.halfExtents);
    if (this.halfExtents.x() < 0 || this.halfExtents.y() < 0 || this.halfExtents.z() < 0)
      throw new IllegalArgumentException("negative half-extent");
  }

  public Vector3dc center() {
    return center;
  }

  public Vector3dc halfExtents() {
    return halfExtents;
  }

  @Override
  public Vector3dc min() {
    return new Vector3d(center).sub(halfExtents, new Vector3d());
  }

  @Override
  public Vector3dc max() {
    return new Vector3d(center).add(halfExtents, new Vector3d());
  }

  @Override
  public double volume() {
    return 8.0 * halfExtents.x() * halfExtents.y() * halfExtents.z();
  }

  @Override
  public boolean contains(Vector3dc p) {
    return Math.abs(p.x() - center.x()) <= halfExtents.x()
        && Math.abs(p.y() - center.y()) <= halfExtents.y()
        && Math.abs(p.z() - center.z()) <= halfExtents.z();
  }

  @Override
  public Bounds bounds(Transform transform) {
    Vector3d c = new Vector3d(transform.position()).add(center, new Vector3d());
    return new Aabb(c, halfExtents);
  }
}
