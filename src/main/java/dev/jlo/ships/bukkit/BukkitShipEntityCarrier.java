package dev.jlo.ships.bukkit;

import dev.jlo.ships.collision.CollisionHull;
import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.ship.ShipEntityCarrier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bukkit implementation of the ship entity carrier. Carries non-ship entities standing on the
 * exposed top hull blocks of a ship when the ship moves vertically, like a honey block.
 *
 * <p>Carry is best-effort: invalid, dead, or world-mismatched entities are skipped, and an entity
 * whose teleport returns false is ignored.
 *
 * <p>Top surfaces are stored in a 2D spatial grid keyed by integer block x/z, so each candidate
 * entity only checks the grid cells under its footprint instead of scanning every top block.
 */
public final class BukkitShipEntityCarrier implements ShipEntityCarrier {
  /** World the ship exists in. */
  private final World world;

  /** Persistent key identifying collision-owner Shulkers. */
  private final NamespacedKey collisionOwnerKey;

  /** Persistent key identifying render-owner BlockDisplays. */
  private final NamespacedKey renderShipKey;

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

    TopSurfaceIndex index = TopSurfaceIndex.build(topExposed, ship, oldY);
    String shipId = ship.id().toString();
    Set<UUID> carried = new HashSet<>();
    for (Entity entity : world.getNearbyEntities(index.bounds())) {
      if (entity.getVehicle() != null) {
        continue;
      }
      if (!entity.isValid() || entity.isDead() || !world.equals(entity.getWorld())) {
        continue;
      }
      if (isShipOwned(entity, shipId)) {
        continue;
      }
      if (!index.overlaps(entity.getBoundingBox())) {
        continue;
      }
      if (carried.add(entity.getUniqueId())) {
        carryEntity(entity, delta);
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

  private static void carryEntity(Entity entity, double delta) {
    Location current = entity.getLocation();
    Location dest =
        new Location(
            current.getWorld(),
            current.getX(),
            current.getY() + delta,
            current.getZ(),
            current.getYaw(),
            current.getPitch());
    try {
      if (!entity.teleport(dest)) {
        Bukkit.getLogger().finest("Ship carry teleport rejected for " + entity.getUniqueId());
      }
    } catch (IllegalArgumentException | IllegalStateException failure) {
      Bukkit.getLogger().finest("Ship carry teleport failed for " + entity.getUniqueId());
    }
  }
}
