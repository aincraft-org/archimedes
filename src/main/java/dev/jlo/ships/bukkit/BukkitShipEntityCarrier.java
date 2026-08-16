package dev.jlo.ships.bukkit;

import dev.jlo.ships.model.Ship;
import dev.jlo.ships.ship.ShipEntityCarrier;
import io.papermc.paper.entity.TeleportFlag;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bukkit implementation of the ship entity carrier. Carries non-ship entities standing on the
 * exposed top hull blocks of a ship when the ship moves vertically, like a honey block.
 *
 * <p>Carry is best-effort: invalid, dead, or world-mismatched entities are skipped, and an entity
 * whose teleport returns false is ignored.
 *
 * <p>Riders are maintained by a {@link BukkitShipRiderTracker}, so a vertical move only teleports
 * the already-known on-board entities instead of scanning nearby entities on every move.
 */
public final class BukkitShipEntityCarrier implements ShipEntityCarrier {
  /** World the ship exists in. */
  private final World world;

  /** Persistent key identifying collision-owner Shulkers. */
  private final NamespacedKey collisionOwnerKey;

  /** Persistent key identifying render-owner BlockDisplays. */
  private final NamespacedKey renderShipKey;

  /** Tracker that maintains the set of entities on each ship. */
  private final BukkitShipRiderTracker tracker;

  /**
   * Creates the Bukkit carrier.
   *
   * @param world the world containing the ship
   * @param collisionOwnerKey the persistent key identifying collision volumes
   * @param renderShipKey the persistent key identifying rendered displays
   * @param tracker the rider tracker that owns the set of on-board entities
   */
  public BukkitShipEntityCarrier(
      World world,
      NamespacedKey collisionOwnerKey,
      NamespacedKey renderShipKey,
      BukkitShipRiderTracker tracker) {
    this.world = world;
    this.collisionOwnerKey = collisionOwnerKey;
    this.renderShipKey = renderShipKey;
    this.tracker = tracker;
  }

  @Override
  public void carry(Ship ship, double oldY, double newY) {
    double delta = newY - oldY;
    if (delta == 0.0) {
      return;
    }

    tracker.track(ship, oldY);
    String shipId = ship.id().toString();
    Set<UUID> riders = tracker.riders(ship);
    if (riders.isEmpty()) {
      return;
    }

    for (UUID entityId : riders) {
      Entity entity = Bukkit.getEntity(entityId);
      if (entity == null || !entity.isValid() || entity.isDead()) {
        continue;
      }
      if (!world.equals(entity.getWorld())) {
        continue;
      }
      if (entity.getVehicle() != null) {
        continue;
      }
      if (isShipOwned(entity, shipId)) {
        continue;
      }
      carryEntity(entity, delta, shipId);
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

  private static void carryEntity(Entity entity, double delta, String shipId) {
    if (entity instanceof Player) {
      entity.setVelocity(entity.getVelocity().add(new org.bukkit.util.Vector(0, delta, 0)));
      return;
    }
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
      if (!entity.teleport(
          dest,
          PlayerTeleportEvent.TeleportCause.PLUGIN,
          TeleportFlag.Relative.VELOCITY_X,
          TeleportFlag.Relative.VELOCITY_Y,
          TeleportFlag.Relative.VELOCITY_Z)) {
        Bukkit.getLogger()
            .finest(
                "Ship carry teleport rejected for ship "
                    + shipId
                    + " and entity "
                    + entity.getUniqueId());
      }
    } catch (IllegalArgumentException | IllegalStateException failure) {
      Bukkit.getLogger()
          .finest(
              "Ship carry teleport failed for ship "
              + shipId
              + " and entity "
              + entity.getUniqueId());
    }
  }
  private static String shipIdForLog(Entity entity) {
    try {
      String collision =
          entity
              .getPersistentDataContainer()
              .get(
                  new org.bukkit.NamespacedKey("ships", "collision-owner"),
                  PersistentDataType.STRING);
      if (collision != null) {
        return collision;
      }
      String render =
          entity
              .getPersistentDataContainer()
              .get(new org.bukkit.NamespacedKey("ships", "ship-id"), PersistentDataType.STRING);
      return render == null ? "<unknown>" : render;
    } catch (RuntimeException ignored) {
      return "<unknown>";
    }
  }
}
