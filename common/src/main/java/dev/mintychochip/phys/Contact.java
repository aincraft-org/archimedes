package dev.mintychochip.phys;

import org.joml.Vector3dc;

/**
 * An AABB contact produced by the octree broadphase or a world-voxel query.
 *
 * @param a first body
 * @param b second body, or {@code null} for a world voxel
 * @param point overlap AABB center
 * @param normal unit axis from {@code a} toward {@code b} (or the voxel)
 * @param penetration overlap along {@code normal}
 */
public record Contact(Body a, Body b, Vector3dc point, Vector3dc normal, double penetration) {
  /**
   * Creates a contact between a body and an infinite-mass world voxel.
   *
   * @param a colliding body
   * @param point overlap AABB center
   * @param normal unit axis from {@code a} toward the voxel
   * @param penetration overlap along {@code normal}
   * @return world contact with a null second body
   */
  public static Contact world(Body a, Vector3dc point, Vector3dc normal, double penetration) {
    return new Contact(a, null, point, normal, penetration);
  }

  /**
   * Reports whether this contact is against the world rather than another body.
   *
   * @return {@code true} when the second body is absent
   */
  public boolean world() {
    return b == null;
  }
}
