package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipTransform;
import java.util.Collection;
import java.util.UUID;

/**
 * Picks the hull a player is standing on or nearest, for commands that cannot use block
 * line-of-sight after assembly.
 */
public final class ShipTargeting {
  /**
   * Extra +Y meters so a player standing or jumping on a deck still counts as on that hull. Matches
   * the carry top-surface jump margin.
   */
  public static final double DECK_MARGIN = 1.5;

  private ShipTargeting() {}

  /**
   * Returns the nearest hull in {@code worldId} whose expanded AABB is within {@code range}, or
   * {@code null} when none qualify.
   *
   * @param ships candidate ships
   * @param worldId world the player occupies
   * @param x player x
   * @param y player y
   * @param z player z
   * @param range maximum distance from the hull AABB
   * @return the nearest in-range hull, or {@code null}
   */
  public static Ship nearest(
      Collection<Ship> ships, UUID worldId, double x, double y, double z, double range) {
    Ship best = null;
    double bestDistance = Double.POSITIVE_INFINITY;
    for (Ship ship : ships) {
      if (!worldId.equals(ship.origin().worldId())) {
        continue;
      }
      double distance = distanceToHull(ship, x, y, z);
      if (distance <= range && distance < bestDistance) {
        best = ship;
        bestDistance = distance;
      }
    }
    return best;
  }

  /**
   * Distance from a point to the ship's expanded hull AABB. Zero when the point is inside the
   * volume, including the standing margin above the deck.
   *
   * @param ship hull
   * @param x world x
   * @param y world y
   * @param z world z
   * @return non-negative distance
   */
  public static double distanceToHull(Ship ship, double x, double y, double z) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;
    boolean any = false;
    for (ShipBlock block : ship.blocks()) {
      ShipTransform.VisualPosition visual = ShipTransform.visual(ship, block.pos());
      any = true;
      minX = Math.min(minX, visual.x());
      minY = Math.min(minY, visual.y());
      minZ = Math.min(minZ, visual.z());
      maxX = Math.max(maxX, visual.x() + 1.0);
      maxY = Math.max(maxY, visual.y() + 1.0 + DECK_MARGIN);
      maxZ = Math.max(maxZ, visual.z() + 1.0);
    }
    if (!any) {
      return Double.POSITIVE_INFINITY;
    }
    double dx = axisDistance(x, minX, maxX);
    double dy = axisDistance(y, minY, maxY);
    double dz = axisDistance(z, minZ, maxZ);
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  private static double axisDistance(double value, double min, double max) {
    if (value < min) {
      return min - value;
    }
    if (value > max) {
      return value - max;
    }
    return 0.0;
  }
}
