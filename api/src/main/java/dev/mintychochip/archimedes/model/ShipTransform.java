package dev.mintychochip.archimedes.model;

/** Canonical projections from a ship model to visual and authoritative coordinates. */
public final class ShipTransform {
  private ShipTransform() {}

  /**
   * Projects a relative block to its exact visual block corner.
   *
   * @param ship ship model
   * @param relative relative block position
   * @return exact visual position
   */
  public static VisualPosition visual(Ship ship, BlockPos relative) {
    return visual(ship, relative, ship.pose().y());
  }

  /**
   * Projects a relative block to its exact visual block corner using a supplied pose y.
   *
   * @param ship ship model
   * @param relative relative block position
   * @param y pose y offset
   * @return exact visual position
   */
  public static VisualPosition visual(Ship ship, BlockPos relative, double y) {
    ShipOrigin origin = ship.origin();
    return new VisualPosition(
        origin.x() + relative.x(), origin.y() + y + relative.y(), origin.z() + relative.z());
  }

  /**
   * Projects a relative block to its authoritative integer cell.
   *
   * @param ship ship model
   * @param relative relative block position
   * @return authoritative cell position
   */
  public static BlockPos cell(Ship ship, BlockPos relative) {
    ShipOrigin origin = ship.origin();
    return new BlockPos(
        origin.x() + relative.x(),
        origin.y() + ship.pose().anchorDy() + relative.y(),
        origin.z() + relative.z());
  }

  /**
   * Projects a relative block to the fractional collision entity anchor.
   *
   * @param ship ship model
   * @param relative relative block position
   * @return centered collision anchor
   */
  public static CollisionAnchor collisionAnchor(Ship ship, BlockPos relative) {
    VisualPosition visual = visual(ship, relative);
    return new CollisionAnchor(visual.x() + 0.5, visual.y(), visual.z() + 0.5);
  }

  /**
   * Fractional world anchor for a collision entity.
   *
   * @param x centered world x coordinate
   * @param y world minimum y coordinate
   * @param z centered world z coordinate
   */
  public record CollisionAnchor(double x, double y, double z) {}

  /**
   * Exact visual position in world coordinates.
   *
   * @param x world x coordinate
   * @param y world y coordinate
   * @param z world z coordinate
   */
  public record VisualPosition(
      /** World x coordinate. */
      double x,
      /** World y coordinate. */
      double y,
      /** World z coordinate. */
      double z) {}
}
