package dev.jlo.ships.bukkit;

import dev.jlo.ships.collision.CollisionHull;
import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipTransform;
import dev.jlo.ships.ship.ShipEntityCarrier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * Bukkit implementation of the ship entity carrier. Carries non-ship entities standing on the
 * exposed top hull blocks of a ship when the ship moves vertically, like a honey block.
 *
 * <p>Carry is best-effort: invalid, dead, or world-mismatched entities are skipped, and an entity
 * whose teleport returns false is ignored.
 */
public final class BukkitShipEntityCarrier implements ShipEntityCarrier {
  /** World the ship exists in. */
  private final World world;

  /** Persistent key identifying collision-owner Shulkers. */
  private final NamespacedKey collisionOwnerKey;

  /** Persistent key identifying render-owner BlockDisplays. */
  private final NamespacedKey renderShipKey;

  /**
   * Vertical margin below the top surface used to catch entities that have just dipped into the
   * block during an upward move.
   */
  private static final double LOWER_MARGIN = 0.5;

  /** Vertical space above the top surface used to catch jumping entities. */
  private static final double UPPER_MARGIN = 2.0;

  /**
   * Creates the Bukkit carrier.
   *
   * @param world the world containing the ship
   * @param collisionOwnerKey the persistent key identifying collision volumes
   * @param renderShipKey the persistent key identifying rendered displays
   */
  public BukkitShipEntityCarrier(
      World world, NamespacedKey collisionOwnerKey, NamespacedKey renderShipKey) {
    this.world = world;
    this.collisionOwnerKey = collisionOwnerKey;
    this.renderShipKey = renderShipKey;
  }

  @Override
  public void carry(Ship ship, double oldY, double newY) {
    double delta = newY - oldY;
    if (delta == 0.0) {
      return;
    }
    List<BlockPos> topExposed = CollisionHull.topExposedBlocks(ship);
    if (topExposed.isEmpty()) {
      return;
    }

    String shipId = ship.id().toString();
    List<TopSurface> surfaces = new ArrayList<>(topExposed.size());
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;

    for (BlockPos relative : topExposed) {
      ShipTransform.VisualPosition visual = ShipTransform.visual(ship, relative, oldY);
      double topY = visual.y() + 1.0;
      double sx = visual.x();
      double sz = visual.z();
      double ex = visual.x() + 1.0;
      double ez = visual.z() + 1.0;

      surfaces.add(new TopSurface(sx, ex, sz, ez, topY - LOWER_MARGIN, topY + UPPER_MARGIN));

      minX = Math.min(minX, sx);
      minY = Math.min(minY, topY - LOWER_MARGIN);
      minZ = Math.min(minZ, sz);
      maxX = Math.max(maxX, ex);
      maxY = Math.max(maxY, topY + UPPER_MARGIN);
      maxZ = Math.max(maxZ, ez);
    }

    Set<UUID> carried = new HashSet<>();
    for (Entity entity :
        world.getNearbyEntities(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ))) {
      if (entity.getVehicle() != null) {
        continue;
      }
      if (!entity.isValid() || entity.isDead() || !world.equals(entity.getWorld())) {
        continue;
      }
      if (isShipOwned(entity, shipId)) {
        continue;
      }
      if (!overlapsTop(entity.getBoundingBox(), surfaces)) {
        continue;
      }
      if (carried.add(entity.getUniqueId())) {
        teleportBy(entity, delta);
      }
    }
  }

  private boolean isShipOwned(Entity entity, String shipId) {
    String collision =
        entity.getPersistentDataContainer().get(collisionOwnerKey, PersistentDataType.STRING);
    if (shipId.equals(collision)) {
      return true;
    }
    String render =
        entity.getPersistentDataContainer().get(renderShipKey, PersistentDataType.STRING);
    return shipId.equals(render);
  }

  private static boolean overlapsTop(BoundingBox box, List<TopSurface> surfaces) {
    for (TopSurface surface : surfaces) {
      if (box.overlaps(surface.box)) {
        return true;
      }
    }
    return false;
  }

  private static void teleportBy(Entity entity, double delta) {
    Location current = entity.getLocation();
    Location dest =
        new Location(
            current.getWorld(),
            current.getX(),
            current.getY() + delta,
            current.getZ(),
            current.getYaw(),
            current.getPitch());
    entity.teleport(dest);
  }

  /** Describes a vertical column above a top-exposed block used to select riders. */
  private static final class TopSurface {
    /** Column bounds above a top-exposed block. */
    final BoundingBox box;

    TopSurface(double minX, double maxX, double minZ, double maxZ, double minY, double maxY) {
      this.box = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
  }
}
