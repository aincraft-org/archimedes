package dev.mintychochip.archimedes.collision;

/**
 * Axis-aligned box used to measure distance to hull cell edges.
 *
 * @param minX inclusive minimum x
 * @param minY inclusive minimum y
 * @param minZ inclusive minimum z
 * @param maxX inclusive maximum x
 * @param maxY inclusive maximum y
 * @param maxZ inclusive maximum z
 */
public record CollisionBox(
    double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
  /**
   * Returns the Euclidean AABB-to-AABB separation. Overlapping boxes have distance {@code 0}.
   *
   * @param other box to measure against
   * @return non-negative edge distance
   */
  public double distance(CollisionBox other) {
    double dx = gap(minX, maxX, other.minX, other.maxX);
    double dy = gap(minY, maxY, other.minY, other.maxY);
    double dz = gap(minZ, maxZ, other.minZ, other.maxZ);
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  /**
   * Returns this box translated by a pose offset.
   *
   * @param dx x translation
   * @param dy y translation
   * @param dz z translation
   * @return shifted box
   */
  public CollisionBox shifted(double dx, double dy, double dz) {
    return new CollisionBox(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
  }

  /**
   * Returns this box expanded by {@code range} on every side.
   *
   * @param range non-negative expansion
   * @return expanded box
   */
  public CollisionBox expanded(double range) {
    return new CollisionBox(
        minX - range, minY - range, minZ - range, maxX + range, maxY + range, maxZ + range);
  }

  /**
   * Returns whether this box intersects {@code other}.
   *
   * @param other box to test
   * @return {@code true} when the boxes overlap
   */
  public boolean intersects(CollisionBox other) {
    return minX <= other.maxX
        && maxX >= other.minX
        && minY <= other.maxY
        && maxY >= other.minY
        && minZ <= other.maxZ
        && maxZ >= other.minZ;
  }

  private static double gap(double minA, double maxA, double minB, double maxB) {
    return Math.max(0.0, Math.max(minA - maxB, minB - maxA));
  }
}
