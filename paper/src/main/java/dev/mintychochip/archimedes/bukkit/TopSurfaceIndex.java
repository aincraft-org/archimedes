package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.util.BoundingBox;

/**
 * Spatial grid of top-exposed hull block columns used to test whether an entity is on a ship. The
 * index stores surfaces at pose {@code (0,0,0)} so it can be reused for any pose by supplying
 * {@code poseX}, {@code poseY}, and {@code poseZ} offsets.
 */
final class TopSurfaceIndex {
  /** Grid keyed by packed integer block x/z. */
  private final Map<Long, TopSurface> grid;

  /** Combined bounds of every top-surface column at pose {@code (0,0,0)}. */
  private final BoundingBox bounds;

  /**
   * Small contact margin below the top surface. Only a tiny amount is needed to tolerate rounding
   * or being pushed down slightly by the ship; larger values make riders clip through the floor.
   */
  private static final double LOWER_MARGIN = 0.05;

  /**
   * Small contact margin above the top surface. Must be enough to keep a walking player on a
   * bobbing ship, but smaller than the ~0.42 blocks/tick of an initial jump so jumping entities are
   * not carried.
   */
  private static final double UPPER_MARGIN = 0.35;

  /**
   * Tolerance used to avoid spuriously including an adjacent cell when an AABB max sits exactly on
   * a block boundary.
   */
  private static final double CELL_EPSILON = 1e-9;

  /**
   * Creates an index whose stored columns are expressed at pose {@code (0,0,0)}.
   *
   * @param grid columns keyed by their integer x/z cell
   * @param bounds aggregate bounds of those columns at pose {@code (0,0,0)}
   */
  private TopSurfaceIndex(Map<Long, TopSurface> grid, BoundingBox bounds) {
    this.grid = grid;
    this.bounds = bounds;
  }

  /**
   * Builds an index from the supplied top-exposed relative blocks and the ship's origin. Surfaces
   * are stored at pose {@code (0,0,0)} and must be shifted by the current pose when queried.
   *
   * @param topExposed top-exposed relative block positions
   * @param ship ship being moved
   * @return a queryable top-surface index
   */
  static TopSurfaceIndex build(List<BlockPos> topExposed, Ship ship) {
    Map<Long, TopSurface> grid = new HashMap<>(topExposed.size() * 2);
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;

    for (BlockPos relative : topExposed) {
      double x = ship.origin().x() + relative.x();
      double y = ship.origin().y() + relative.y();
      double z = ship.origin().z() + relative.z();
      double topY = y + 1.0;
      double lower = topY - LOWER_MARGIN;
      double upper = topY + UPPER_MARGIN;

      TopSurface surface = new TopSurface(x, lower, z, x + 1.0, upper, z + 1.0);
      long key = pack((int) Math.floor(x), (int) Math.floor(z));
      grid.put(key, surface);

      minX = Math.min(minX, x);
      minY = Math.min(minY, lower);
      minZ = Math.min(minZ, z);
      maxX = Math.max(maxX, x + 1.0);
      maxY = Math.max(maxY, upper);
      maxZ = Math.max(maxZ, z + 1.0);
    }

    return new TopSurfaceIndex(grid, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
  }

  /**
   * Returns the combined bounds of all indexed top-surface columns shifted by the supplied pose y.
   *
   * @param poseY current ship pose y offset
   * @return bounding box that spans every indexed column at the given pose
   */
  BoundingBox bounds(double poseY) {
    return bounds(0.0, poseY, 0.0);
  }

  /**
   * Returns the combined bounds of all indexed top-surface columns shifted by the supplied pose.
   *
   * @param poseX current ship pose x offset
   * @param poseY current ship pose y offset
   * @param poseZ current ship pose z offset
   * @return bounding box that spans every indexed column at the given pose
   */
  BoundingBox bounds(double poseX, double poseY, double poseZ) {
    return new BoundingBox(
        bounds.getMinX() + poseX,
        bounds.getMinY() + poseY,
        bounds.getMinZ() + poseZ,
        bounds.getMaxX() + poseX,
        bounds.getMaxY() + poseY,
        bounds.getMaxZ() + poseZ);
  }

  /**
   * Returns true when the entity box overlaps any indexed top-surface column at the supplied pose
   * y.
   *
   * @param entityBox entity bounding box
   * @param poseY current ship pose y offset
   * @return true if the entity is on a top surface
   */
  boolean overlaps(BoundingBox entityBox, double poseY) {
    return overlaps(entityBox, 0.0, poseY, 0.0);
  }

  /**
   * Returns true when the entity box overlaps any indexed top-surface column at the supplied pose.
   *
   * @param entityBox entity bounding box
   * @param poseX current ship pose x offset
   * @param poseY current ship pose y offset
   * @param poseZ current ship pose z offset
   * @return true if the entity is on a top surface
   */
  boolean overlaps(BoundingBox entityBox, double poseX, double poseY, double poseZ) {
    int minX = (int) Math.floor(entityBox.getMinX() - poseX);
    int maxX = (int) Math.floor(entityBox.getMaxX() - poseX - CELL_EPSILON);
    int minZ = (int) Math.floor(entityBox.getMinZ() - poseZ);
    int maxZ = (int) Math.floor(entityBox.getMaxZ() - poseZ - CELL_EPSILON);

    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        TopSurface surface = grid.get(pack(x, z));
        if (surface != null && overlapsShifted(entityBox, surface, poseX, poseY, poseZ)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean overlapsShifted(
      BoundingBox entityBox, TopSurface surface, double poseX, double poseY, double poseZ) {
    double minX = surface.box.getMinX() + poseX;
    double maxX = surface.box.getMaxX() + poseX;
    double minY = surface.box.getMinY() + poseY;
    double maxY = surface.box.getMaxY() + poseY;
    double minZ = surface.box.getMinZ() + poseZ;
    double maxZ = surface.box.getMaxZ() + poseZ;
    double footY = entityBox.getMinY();
    return entityBox.getMinX() < maxX
        && entityBox.getMaxX() > minX
        && entityBox.getMinZ() < maxZ
        && entityBox.getMaxZ() > minZ
        && footY >= minY
        && footY <= maxY;
  }

  private static long pack(int x, int z) {
    return ((long) x << 32) | (z & 0xffffffffL);
  }

  /** Describes a vertical column above a top-exposed block used to select riders. */
  private static final class TopSurface {
    /** Column bounds above a top-exposed block at pose {@code (0,0,0)}. */
    final BoundingBox box;

    TopSurface(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      this.box = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
  }
}
