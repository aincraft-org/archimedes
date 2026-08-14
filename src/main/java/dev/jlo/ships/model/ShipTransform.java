package dev.jlo.ships.model;

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
    ShipOrigin origin = ship.origin();
    return new VisualPosition(
        origin.x() + relative.x(),
        origin.y() + ship.pose().y() + relative.y(),
        origin.z() + relative.z());
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
