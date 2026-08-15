package dev.jlo.ships.bukkit;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipTransform;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.util.BoundingBox;

/** Spatial grid of top-exposed hull block columns used to test whether an entity is on a ship. */
final class TopSurfaceIndex {
  /** Grid keyed by packed integer block x/z. */
  private final Map<Long, TopSurface> grid;

  /** Combined bounds of every top-surface column. */
  private final BoundingBox bounds;

  /**
   * Vertical margin below the top surface used to catch entities that have just dipped into the
   * block during an upward move.
   */
  private static final double LOWER_MARGIN = 0.5;

  /** Vertical space above the top surface used to catch jumping entities. */
  private static final double UPPER_MARGIN = 2.0;

  /**
   * Tolerance used to avoid spuriously including an adjacent cell when an AABB max sits exactly on
   * a block boundary.
   */
  private static final double CELL_EPSILON = 1e-9;

  private TopSurfaceIndex(Map<Long, TopSurface> grid, BoundingBox bounds) {
    this.grid = grid;
    this.bounds = bounds;
  }

  /**
   * Builds an index from the supplied top-exposed relative blocks and the ship's old pose.
   *
   * @param topExposed top-exposed relative block positions
   * @param ship ship being moved
   * @param oldY previous pose y
   * @return a queryable top-surface index
   */
  static TopSurfaceIndex build(List<BlockPos> topExposed, Ship ship, double oldY) {
    Map<Long, TopSurface> grid = new HashMap<>(topExposed.size() * 2);
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;

    for (BlockPos relative : topExposed) {
      ShipTransform.VisualPosition visual = ShipTransform.visual(ship, relative, oldY);
      double topY = visual.y() + 1.0;
      double lower = topY - LOWER_MARGIN;
      double upper = topY + UPPER_MARGIN;

      TopSurface surface =
          new TopSurface(visual.x(), lower, visual.z(), visual.x() + 1.0, upper, visual.z() + 1.0);
      long key = pack((int) Math.floor(visual.x()), (int) Math.floor(visual.z()));
      grid.put(key, surface);

      minX = Math.min(minX, visual.x());
      minY = Math.min(minY, lower);
      minZ = Math.min(minZ, visual.z());
      maxX = Math.max(maxX, visual.x() + 1.0);
      maxY = Math.max(maxY, upper);
      maxZ = Math.max(maxZ, visual.z() + 1.0);
    }

    return new TopSurfaceIndex(grid, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
  }

  /**
   * Returns the combined bounds of all indexed top-surface columns.
   *
   * @return bounding box that spans every indexed column
   */
  BoundingBox bounds() {
    return bounds;
  }

  /**
   * Returns true when the entity box overlaps any indexed top-surface column.
   *
   * @param entityBox entity bounding box
   * @return true if the entity is on a top surface
   */
  boolean overlaps(BoundingBox entityBox) {
    int minX = (int) Math.floor(entityBox.getMinX());
    int maxX = (int) Math.floor(entityBox.getMaxX() - CELL_EPSILON);
    int minZ = (int) Math.floor(entityBox.getMinZ());
    int maxZ = (int) Math.floor(entityBox.getMaxZ() - CELL_EPSILON);

    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        TopSurface surface = grid.get(pack(x, z));
        if (surface != null && entityBox.overlaps(surface.box)) {
          return true;
        }
      }
    }
    return false;
  }

  private static long pack(int x, int z) {
    return ((long) x << 32) | (z & 0xffffffffL);
  }

  /** Describes a vertical column above a top-exposed block used to select riders. */
  private static final class TopSurface {
    /** Column bounds above a top-exposed block. */
    final BoundingBox box;

    TopSurface(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      this.box = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
  }
}
