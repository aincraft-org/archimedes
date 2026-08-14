package dev.jlo.ships.model;

/** Canonical projections from a ship model to visual and authoritative coordinates. */
public final class ShipTransform {
  private ShipTransform() {}

  /** Projects a relative block to its exact visual block corner. */
  public static VisualPosition visual(Ship ship, BlockPos relative) {
    ShipOrigin origin = ship.origin();
    return new VisualPosition(
        origin.x() + relative.x(),
        origin.y() + ship.pose().y() + relative.y(),
        origin.z() + relative.z());
  }

  /** Projects a relative block to its authoritative integer cell. */
  public static BlockPos cell(Ship ship, BlockPos relative) {
    ShipOrigin origin = ship.origin();
    return new BlockPos(
        origin.x() + relative.x(),
        origin.y() + ship.pose().anchorDy() + relative.y(),
        origin.z() + relative.z());
  }

  /** Exact visual position in world coordinates. */
  public record VisualPosition(double x, double y, double z) {}
}
