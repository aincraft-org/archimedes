package dev.jlo.ships.bukkit;

import dev.jlo.ships.collision.CollisionHull;
import dev.jlo.ships.model.Ship;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * Maintains a persistent set of entities that are standing on each ship's exposed top hull. The
 * tracker listens to Bukkit entity events and uses a cached {@link TopSurfaceIndex} per ship, so a
 * vertical ship move only teleports the already-known riders instead of scanning the surrounding
 * area.
 */
public final class BukkitShipRiderTracker implements Listener {
  /** World the ships exist in. */
  private final World world;

  /** Supplier of all currently registered ships. */
  private final Supplier<Collection<Ship>> allShips;

  /** Key identifying collision-owner Shulkers. */
  private final NamespacedKey collisionOwnerKey;

  /** Key identifying render-owner BlockDisplays. */
  private final NamespacedKey renderShipKey;

  /** Cached top-surface index for each ship, keyed by ship id. */
  private final Map<UUID, TopSurfaceIndex> indices = new HashMap<>();

  /** Rider sets keyed by ship id. */
  private final Map<UUID, Set<UUID>> ridersByShip = new HashMap<>();

  /** Stored pose basis keyed by ship id. */
  private final Map<UUID, Double> poseBasisByShip = new HashMap<>();

  /** Reverse lookup from entity id to the ship it is currently riding. */
  private final Map<UUID, UUID> shipByEntity = new HashMap<>();

  /**
   * Creates the tracker.
   *
   * @param world the world containing the ships
   * @param allShips supplier of all registered ships
   * @param collisionOwnerKey key identifying ship collision volumes
   * @param renderShipKey key identifying ship render displays
   */
  public BukkitShipRiderTracker(
      World world,
      Supplier<Collection<Ship>> allShips,
      NamespacedKey collisionOwnerKey,
      NamespacedKey renderShipKey) {
    this.world = world;
    this.allShips = allShips;
    this.collisionOwnerKey = collisionOwnerKey;
    this.renderShipKey = renderShipKey;
  }

  /**
   * Ensures the ship is indexed and performs an initial scan for any entities already standing on
   * it. This is called once per ship the first time it is carried.
   *
   * @param ship the ship to track
   * @param seedPoseY the pose y to use when locating existing riders, typically the ship's old y
   */
  public void track(Ship ship, double seedPoseY) {
    UUID shipId = ship.id();
    poseBasisByShip.put(shipId, seedPoseY);
    boolean added =
        indices.putIfAbsent(
                shipId, TopSurfaceIndex.build(CollisionHull.topExposedBlocks(ship), ship))
            == null;
    if (added) {
      seedRiders(ship, seedPoseY);
    }
  }

  /**
   * Updates the stored pose basis used by event-time overlap checks.
   *
   * @param ship ship whose basis is updated
   * @param poseY pose basis to store
   */
  public void updatePoseBasis(Ship ship, double poseY) {
    poseBasisByShip.put(ship.id(), poseY);
  }

  /** Clears every index and rider association. */
  public void clear() {
    indices.clear();
    ridersByShip.clear();
    poseBasisByShip.clear();
    shipByEntity.clear();
  }

  /**
   * Returns the set of entity ids currently on board the ship.
   *
   * @param ship the ship to query
   * @return the current riders of the ship
   */
  public Set<UUID> riders(Ship ship) {
    return ridersByShip.computeIfAbsent(ship.id(), k -> new HashSet<>());
  }

  /**
   * Builds or rebuilds the top-surface index for a ship, for example after the ship has been
   * modified. Existing rider state is preserved.
   *
   * @param ship the ship to refresh
   */
  public void refresh(Ship ship) {
    indices.put(ship.id(), TopSurfaceIndex.build(CollisionHull.topExposedBlocks(ship), ship));
  }

  /**
   * Stops tracking a ship and drops its riders.
   *
   * @param ship the ship to untrack
   */
  public void untrack(Ship ship) {
    UUID shipId = ship.id();
    Set<UUID> riders = ridersByShip.remove(shipId);
    if (riders != null) {
      for (UUID rider : riders) {
        shipByEntity.remove(rider);
      }
    }
    poseBasisByShip.remove(shipId);
    indices.remove(shipId);
  }

  private void seedRiders(Ship ship, double poseY) {
    TopSurfaceIndex index = indices.get(ship.id());
    if (index == null) {
      return;
    }
    for (Entity entity : world.getNearbyEntities(index.bounds(poseY))) {
      if (!canRide(entity)) {
        continue;
      }
      if (index.overlaps(entity.getBoundingBox(), poseY)) {
        addRider(ship.id(), entity.getUniqueId());
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerMove(PlayerMoveEvent event) {
    updateAtLocation(event.getPlayer(), event.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntityMove(io.papermc.paper.event.entity.EntityMoveEvent event) {
    updateAtLocation(event.getEntity(), event.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onVehicleMove(VehicleMoveEvent event) {
    updateAtLocation(event.getVehicle(), event.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntitySpawn(EntitySpawnEvent event) {
    updateAtLocation(event.getEntity(), event.getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onItemSpawn(ItemSpawnEvent event) {
    updateAtLocation(event.getEntity(), event.getEntity().getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onVehicleEnter(VehicleEnterEvent event) {
    removeRider(event.getEntered().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onVehicleExit(VehicleExitEvent event) {
    updateAtLocation(event.getExited(), event.getExited().getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntityTeleport(EntityTeleportEvent event) {
    Location to = event.getTo();
    if (to == null) {
      removeRider(event.getEntity().getUniqueId());
    } else {
      updateAtLocation(event.getEntity(), to);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerTeleport(PlayerTeleportEvent event) {
    updateAtLocation(event.getPlayer(), event.getTo());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
    removeRider(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntityDeath(EntityDeathEvent event) {
    removeRider(event.getEntity().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerQuit(PlayerQuitEvent event) {
    removeRider(event.getPlayer().getUniqueId());
  }

  private void updateAtLocation(Entity entity, Location location) {
    if (location == null || !world.equals(location.getWorld())) {
      removeRider(entity.getUniqueId());
      return;
    }
    if (!canRide(entity)) {
      removeRider(entity.getUniqueId());
      return;
    }

    UUID entityId = entity.getUniqueId();
    UUID previousShip = shipByEntity.get(entityId);
    Ship chosen = null;

    for (Ship ship : allShips.get()) {
      if (!ship.origin().worldId().equals(world.getUID())) {
        continue;
      }
      if (previousShip != null && !ship.id().equals(previousShip)) {
        continue;
      }
      TopSurfaceIndex index = indexFor(ship);
      double poseY = poseBasisByShip.getOrDefault(ship.id(), 0.0);
      if (index.overlaps(boundsAt(entity, location), poseY)) {
        chosen = ship;
        break;
      }
    }

    if (chosen == null) {
      for (Ship ship : allShips.get()) {
        if (!ship.origin().worldId().equals(world.getUID())) {
          continue;
        }
        if (previousShip != null && ship.id().equals(previousShip)) {
          continue;
        }
        TopSurfaceIndex index = indexFor(ship);
        double poseY = poseBasisByShip.getOrDefault(ship.id(), 0.0);
        if (index.overlaps(boundsAt(entity, location), poseY)) {
          chosen = ship;
          break;
        }
      }
    }

    if (chosen == null) {
      removeRider(entityId);
      return;
    }
    addRider(chosen.id(), entityId);
  }

  private TopSurfaceIndex indexFor(Ship ship) {
    return indices.computeIfAbsent(
        ship.id(), k -> TopSurfaceIndex.build(CollisionHull.topExposedBlocks(ship), ship));
  }

  private BoundingBox boundsAt(Entity entity, Location location) {
    double halfWidth = entity.getWidth() / 2.0;
    double x = location.getX();
    double y = location.getY();
    double z = location.getZ();
    return new BoundingBox(
        x - halfWidth, y, z - halfWidth, x + halfWidth, y + entity.getHeight(), z + halfWidth);
  }

  private boolean canRide(Entity entity) {
    if (entity.getVehicle() != null) {
      return false;
    }
    if (!entity.isValid() || entity.isDead()) {
      return false;
    }
    if (isShipOwned(entity)) {
      return false;
    }
    return true;
  }

  private boolean isShipOwned(Entity entity) {
    String collision =
        entity.getPersistentDataContainer().get(collisionOwnerKey, PersistentDataType.STRING);
    if (collision != null && !collision.isEmpty()) {
      return true;
    }
    String render =
        entity.getPersistentDataContainer().get(renderShipKey, PersistentDataType.STRING);
    return render != null && !render.isEmpty();
  }

  private void addRider(UUID shipId, UUID entityId) {
    ridersByShip.computeIfAbsent(shipId, k -> new HashSet<>()).add(entityId);
    shipByEntity.put(entityId, shipId);
  }

  private void removeRider(UUID entityId) {
    UUID shipId = shipByEntity.remove(entityId);
    if (shipId == null) {
      return;
    }
    Set<UUID> riders = ridersByShip.get(shipId);
    if (riders != null) {
      riders.remove(entityId);
    }
  }
}
