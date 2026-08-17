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

  /**
   * Creates an axis-aligned box.
   *
   * @param center box center in local coordinates
   * @param halfExtents non-negative finite distances from the center to each face
   * @throws NullPointerException if either vector is {@code null}
   * @throws IllegalArgumentException if a component is non-finite or negative
   */
  public Aabb(Vector3dc center, Vector3dc halfExtents) {
    this.center = new Vector3d(Objects.requireNonNull(center));
    this.halfExtents = new Vector3d(Objects.requireNonNull(halfExtents));
    Vectors.requireFinite(this.center);
    Vectors.requireFinite(this.halfExtents);
    if (this.halfExtents.x() < 0 || this.halfExtents.y() < 0 || this.halfExtents.z() < 0)
      throw new IllegalArgumentException("negative half-extent");
  }

  /**
   * @return the box center vector
   */
  public Vector3dc center() {
    return center;
  }

  /**
   * @return the non-negative half-extents along the x, y, and z axes
   */
  public Vector3dc halfExtents() {
    return halfExtents;
  }

  /**
   * Returns the minimum corner, inclusive of the box boundary.
   *
   * @return a new vector containing {@code center - halfExtents}
   */
  @Override
  public Vector3dc min() {
    return new Vector3d(center).sub(halfExtents, new Vector3d());
  }

  /**
   * Returns the maximum corner, inclusive of the box boundary.
   *
   * @return a new vector containing {@code center + halfExtents}
   */
  @Override
  public Vector3dc max() {
    return new Vector3d(center).add(halfExtents, new Vector3d());
  }

  /**
   * Computes the box volume.
   *
   * @return the product of the three full axis lengths
   */
  @Override
  public double volume() {
    return 8.0 * halfExtents.x() * halfExtents.y() * halfExtents.z();
  }

  /**
   * Tests whether a point lies inside or on the box boundary.
   *
   * @param p point to test
   * @return whether each coordinate is within its corresponding half-extent
   */
  @Override
  public boolean contains(Vector3dc p) {
    return Math.abs(p.x() - center.x()) <= halfExtents.x()
        && Math.abs(p.y() - center.y()) <= halfExtents.y()
        && Math.abs(p.z() - center.z()) <= halfExtents.z();
  }

  /**
   * Translates this local box by a transform's position.
   *
   * @param transform transform supplying the world-space translation
   * @return translated bounds; orientation does not affect an axis-aligned box
   */
  @Override
  public Bounds bounds(Transform transform) {
    Vector3d c = new Vector3d(transform.position()).add(center, new Vector3d());
    return new Aabb(c, halfExtents);
  }
}
