package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.ship.ShipEntityCarrier;
import io.papermc.paper.entity.TeleportFlag;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bukkit implementation of the ship entity carrier. Carries non-ship entities standing on the
 * exposed top hull blocks of a ship when the ship moves, like standing on a vehicle.
 *
 * <p>Carry is best-effort: invalid, dead, or world-mismatched entities are skipped, and an entity
 * whose teleport returns false is ignored.
 *
 * <p>Players and other riders are teleported by the same pose delta as the hull so standing still
 * stays on the deck and a jump is not cancelled. Riders are maintained by a {@link
 * BukkitShipRiderTracker}.
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

  /**
   * Starts tracking entities for a ship and records its current vertical pose basis.
   *
   * @param ship the ship whose riders are tracked
   * @param poseY the ship's current vertical pose
   */
  @Override
  public void track(Ship ship, double poseY) {
    tracker.track(ship, poseY);
  }

  /**
   * Starts tracking entities for a ship and records its current pose basis.
   *
   * @param ship the ship whose riders are tracked
   * @param pose the ship's current pose
   */
  @Override
  public void track(Ship ship, ShipPose pose) {
    tracker.track(ship, pose);
  }

  /**
   * Stops tracking a ship and removes its rider associations.
   *
   * @param ship the ship to stop tracking
   */
  @Override
  public void untrack(Ship ship) {
    tracker.untrack(ship);
  }

  /** Clears all tracked ships, pose bases, rider associations, and entity indexes. */
  @Override
  public void clear() {
    tracker.clear();
  }

  /**
   * Updates the remembered vertical pose basis without carrying entities.
   *
   * @param ship the ship whose basis is updated
   * @param poseY the new vertical pose basis
   */
  @Override
  public void updatePoseBasis(Ship ship, double poseY) {
    tracker.updatePoseBasis(ship, poseY);
  }

  /**
   * Updates the remembered pose basis without carrying entities.
   *
   * @param ship the ship whose basis is updated
   * @param pose the new pose basis
   */
  @Override
  public void updatePoseBasis(Ship ship, ShipPose pose) {
    tracker.updatePoseBasis(ship, pose);
  }

  /**
   * Carries currently tracked, valid, same-world, non-vehicle, non-ship entities by the ship's pose
   * delta. Player velocity is adjusted directly; other entities are teleported when possible. A
   * zero movement delta performs no work.
   *
   * @param ship the ship that moved
   * @param oldY the previous vertical pose
   * @param newY the new vertical pose
   */
  @Override
  public void carry(Ship ship, double oldY, double newY) {
    carry(
        ship,
        new ShipPose(ship.pose().x(), oldY, ship.pose().z()),
        new ShipPose(ship.pose().x(), newY, ship.pose().z()));
  }

  /**
   * Carries currently tracked riders by the full pose delta so a sail step that only changes XZ
   * still keeps players and mobs on the deck.
   *
   * @param ship the ship that moved
   * @param from previous pose
   * @param to new pose
   */
  @Override
  public void carry(Ship ship, ShipPose from, ShipPose to) {
    double dx = to.x() - from.x();
    double dy = to.y() - from.y();
    double dz = to.z() - from.z();
    if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
      return;
    }

    tracker.updatePoseBasis(ship, to);
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
      carryEntity(entity, dx, dy, dz, shipId);
      tracker.retain(ship, entityId);
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

  /**
   * Applies one carry step by teleporting the rider the same pose delta as the hull.
   *
   * @param entity rider to move
   * @param dx ship pose delta x
   * @param dy ship pose delta y
   * @param dz ship pose delta z
   * @param shipId ship id for log context
   */
  private static void carryEntity(Entity entity, double dx, double dy, double dz, String shipId) {
    Location current = entity.getLocation();
    Location dest =
        new Location(
            current.getWorld(),
            current.getX() + dx,
            current.getY() + dy,
            current.getZ() + dz,
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

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private static String shipIdForLog(Entity entity) {
    try {
      String collision =
          entity
              .getPersistentDataContainer()
              .get(
                  new org.bukkit.NamespacedKey("archimedes", "collision-owner"),
                  PersistentDataType.STRING);
      if (collision != null) {
        return collision;
      }
      String render =
          entity
              .getPersistentDataContainer()
              .get(
                  new org.bukkit.NamespacedKey("archimedes", "ship-id"), PersistentDataType.STRING);
      return render == null ? "<unknown>" : render;
    } catch (RuntimeException ignored) {
      return "<unknown>";
    }
  }
}
