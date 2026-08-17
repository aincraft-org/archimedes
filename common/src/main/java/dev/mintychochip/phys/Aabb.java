package dev.mintychochip.phys;

import java.util.Objects;

/** Axis-aligned box shape and bounds. */
public final class Aabb implements Shape, Bounds {
  /** Box center. */
  private final Vector3 center;

  /** Non-negative half extents. */
  private final Vector3 halfExtents;

  public Aabb(Vector3 center, Vector3 halfExtents) {
    this.center = Objects.requireNonNull(center);
    this.halfExtents = Objects.requireNonNull(halfExtents);
    if (halfExtents.x() < 0 || halfExtents.y() < 0 || halfExtents.z() < 0)
      throw new IllegalArgumentException("negative half-extent");
  }

  public Vector3 center() {
    return center;
  }

  public Vector3 halfExtents() {
    return halfExtents;
  }

  @Override
  public Vector3 min() {
    return new Vector3(
        center.x() - halfExtents.x(), center.y() - halfExtents.y(), center.z() - halfExtents.z());
  }

  @Override
  public Vector3 max() {
    return new Vector3(
        center.x() + halfExtents.x(), center.y() + halfExtents.y(), center.z() + halfExtents.z());
  }

  @Override
  public double volume() {
    return 8.0 * halfExtents.x() * halfExtents.y() * halfExtents.z();
  }

  @Override
  public boolean contains(Vector3 p) {
    return Math.abs(p.x() - center.x()) <= halfExtents.x()
        && Math.abs(p.y() - center.y()) <= halfExtents.y()
        && Math.abs(p.z() - center.z()) <= halfExtents.z();
  }

  @Override
  public Bounds bounds(Transform transform) {
    Vector3 c = transform.position().add(center);
    return new Aabb(c, halfExtents);
  }
}
