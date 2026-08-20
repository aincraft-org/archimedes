package dev.mintychochip.archimedes.collision;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exposed hull cells stored at pose zero so queries can shift by the current ship pose.
 *
 * <p>A cell is needed when the AABB-to-AABB distance from an observer box to the cell box is at
 * most the supplied range.
 */
public final class ExposedCellIndex {
  /** Distance at which an observer starts holding a cell. */
  public static final double ENTER_RANGE = 4.0;

  /** Distance beyond which an observer releases a previously held cell. */
  public static final double LEAVE_RANGE = 6.0;

  /** Relative cells with their pose-zero world boxes. */
  private final List<Cell> cells;

  /** Union of pose-zero cell boxes, or {@code null} when the hull has no exposed cells. */
  private final CollisionBox bounds;

  private ExposedCellIndex(List<Cell> cells, CollisionBox bounds) {
    this.cells = cells;
    this.bounds = bounds;
  }

  /**
   * Builds an index of every exposed cell on {@code ship} at pose zero.
   *
   * @param ship ship whose exposed cells are indexed
   * @return queryable index
   */
  public static ExposedCellIndex build(Vehicle ship) {
    ShipOrigin origin = ship.origin();
    List<Cell> cells = new ArrayList<>();
    CollisionBox bounds = null;
    for (BlockPos relative : CollisionHull.exposedBlocks(ship)) {
      double x = origin.x() + relative.x();
      double y = origin.y() + relative.y();
      double z = origin.z() + relative.z();
      CollisionBox box = new CollisionBox(x, y, z, x + 1.0, y + 1.0, z + 1.0);
      cells.add(new Cell(relative, box));
      bounds = bounds == null ? box : union(bounds, box);
    }
    return new ExposedCellIndex(List.copyOf(cells), bounds);
  }

  /**
   * Returns exposed cells whose pose-shifted box is within {@code range} of {@code observer}.
   *
   * @param observer observer bounding box in world space
   * @param poseX current pose x
   * @param poseY current pose y
   * @param poseZ current pose z
   * @param range maximum edge distance
   * @return matching relative cells in lexicographic order
   */
  public List<BlockPos> cellsWithin(
      CollisionBox observer, double poseX, double poseY, double poseZ, double range) {
    List<BlockPos> matches = new ArrayList<>();
    for (Cell cell : cells) {
      CollisionBox shifted = cell.box.shifted(poseX, poseY, poseZ);
      if (shifted.distance(observer) <= range) {
        matches.add(cell.relative);
      }
    }
    matches.sort(
        Comparator.comparingInt(BlockPos::x)
            .thenComparingInt(BlockPos::y)
            .thenComparingInt(BlockPos::z));
    return List.copyOf(matches);
  }

  /**
   * Returns the union of indexed cell boxes shifted by the current pose, or {@code null} when
   * empty.
   *
   * @param poseX current pose x
   * @param poseY current pose y
   * @param poseZ current pose z
   * @return shifted hull bounds
   */
  public CollisionBox bounds(double poseX, double poseY, double poseZ) {
    return bounds == null ? null : bounds.shifted(poseX, poseY, poseZ);
  }

  /**
   * Returns the number of indexed exposed cells.
   *
   * @return exposed cell count
   */
  public int size() {
    return cells.size();
  }

  private static CollisionBox union(CollisionBox a, CollisionBox b) {
    return new CollisionBox(
        Math.min(a.minX(), b.minX()),
        Math.min(a.minY(), b.minY()),
        Math.min(a.minZ(), b.minZ()),
        Math.max(a.maxX(), b.maxX()),
        Math.max(a.maxY(), b.maxY()),
        Math.max(a.maxZ(), b.maxZ()));
  }

  /**
   * One exposed cell and its pose-zero world box.
   *
   * @param relative cell relative to the ship origin
   * @param box world box at pose zero
   */
  private record Cell(BlockPos relative, CollisionBox box) {}
}
