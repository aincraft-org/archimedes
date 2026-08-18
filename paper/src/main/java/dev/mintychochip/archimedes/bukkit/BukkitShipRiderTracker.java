package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.collision.CollisionHull;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipPose;
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
 * Maintains an in-memory set of entities that are standing on each ship's exposed top hull. The
 * tracker listens to Bukkit entity events and uses a cached {@link TopSurfaceIndex} per ship, so a
 * ship move only teleports the already-known riders instead of scanning the surrounding area.
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
  private final Map<UUID, ShipPose> poseBasisByShip = new HashMap<>();

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
    track(ship, new ShipPose(ship.pose().x(), seedPoseY, ship.pose().z()));
  }

  /**
   * Ensures the ship is indexed and performs an initial scan for any entities already standing on
   * it. This is called once per ship the first time it is carried.
   *
   * @param ship the ship to track
   * @param seedPose the pose to use when locating existing riders
   */
  public void track(Ship ship, ShipPose seedPose) {
    UUID shipId = ship.id();
    poseBasisByShip.put(shipId, seedPose);
    boolean added =
        indices.putIfAbsent(
                shipId, TopSurfaceIndex.build(CollisionHull.topExposedBlocks(ship), ship))
            == null;
    if (added) {
      seedRiders(ship, seedPose);
    }
  }

  /**
   * Updates the stored pose basis used by event-time overlap checks.
   *
   * @param ship ship whose basis is updated
   * @param poseY pose basis to store
   */
  public void updatePoseBasis(Ship ship, double poseY) {
    updatePoseBasis(ship, new ShipPose(ship.pose().x(), poseY, ship.pose().z()));
  }

  /**
   * Updates the stored pose basis used by event-time overlap checks.
   *
   * @param ship ship whose basis is updated
   * @param pose pose basis to store
   */
  public void updatePoseBasis(Ship ship, ShipPose pose) {
    poseBasisByShip.put(ship.id(), pose);
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

  /**
   * Scans the area above the ship's top surface for any entities that can ride it and records them
   * as riders.
   *
   * @param ship the ship to seed
   * @param pose the pose basis to use for overlap checks
   */
  private void seedRiders(Ship ship, ShipPose pose) {
    TopSurfaceIndex index = indices.get(ship.id());
    if (index == null) {
      return;
    }
    for (Entity entity : world.getNearbyEntities(index.bounds(pose.x(), pose.y(), pose.z()))) {
      if (!canRide(entity)) {
        continue;
      }
      if (index.overlaps(entity.getBoundingBox(), pose.x(), pose.y(), pose.z())) {
        addRider(ship.id(), entity.getUniqueId());
      }
    }
  }

  /**
   * Re-evaluates a player after movement, retaining the rider association only while it overlaps a
   * ship surface.
   *
   * @param event movement event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerMove(PlayerMoveEvent event) {
    updateAtLocation(event.getPlayer(), event.getTo());
  }

  /**
   * Updates rider state for non-player entity movement.
   *
   * @param event entity movement event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntityMove(io.papermc.paper.event.entity.EntityMoveEvent event) {
    updateAtLocation(event.getEntity(), event.getTo());
  }

  /**
   * Updates rider state when a vehicle moves.
   *
   * @param event vehicle movement event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onVehicleMove(VehicleMoveEvent event) {
    updateAtLocation(event.getVehicle(), event.getTo());
  }

  /**
   * Considers newly spawned entities for immediate ship boarding.
   *
   * @param event entity spawn event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntitySpawn(EntitySpawnEvent event) {
    updateAtLocation(event.getEntity(), event.getLocation());
  }

  /**
   * Considers newly spawned items for immediate ship boarding.
   *
   * @param event item spawn event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onItemSpawn(ItemSpawnEvent event) {
    updateAtLocation(event.getEntity(), event.getEntity().getLocation());
  }

  /**
   * Removes an entity from rider tracking when it enters a vehicle.
   *
   * @param event vehicle entry event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onVehicleEnter(VehicleEnterEvent event) {
    removeRider(event.getEntered().getUniqueId());
  }

  /**
   * Re-evaluates an entity after it exits a vehicle.
   *
   * @param event vehicle exit event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onVehicleExit(VehicleExitEvent event) {
    updateAtLocation(event.getExited(), event.getExited().getLocation());
  }

  /**
   * Re-evaluates an entity after teleport, removing it when no destination exists.
   *
   * @param event entity teleport event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntityTeleport(EntityTeleportEvent event) {
    Location to = event.getTo();
    if (to == null) {
      removeRider(event.getEntity().getUniqueId());
    } else {
      updateAtLocation(event.getEntity(), to);
    }
  }

  /**
   * Re-evaluates a player after teleport.
   *
   * @param event player teleport event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerTeleport(PlayerTeleportEvent event) {
    updateAtLocation(event.getPlayer(), event.getTo());
  }

  /**
   * Removes a player when it changes worlds.
   *
   * @param event world-change event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
    removeRider(event.getPlayer().getUniqueId());
  }

  /**
   * Removes dead entities from rider tracking.
   *
   * @param event entity death event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onEntityDeath(EntityDeathEvent event) {
    removeRider(event.getEntity().getUniqueId());
  }

  /**
   * Removes players that leave the server from rider tracking.
   *
   * @param event player quit event
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  void onPlayerQuit(PlayerQuitEvent event) {
    removeRider(event.getPlayer().getUniqueId());
  }

  /**
   * Evaluates the entity's position against all candidate ships and updates the rider association.
   * Prefers the ship it was already riding, then falls back to any overlapping ship.
   *
   * @param entity the entity to evaluate
   * @param location the position to test
   */
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
      ShipPose pose = poseBasis(ship);
      if (index.overlaps(boundsAt(entity, location), pose.x(), pose.y(), pose.z())) {
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
        ShipPose pose = poseBasis(ship);
        if (index.overlaps(boundsAt(entity, location), pose.x(), pose.y(), pose.z())) {
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

  /**
   * Returns the cached top-surface index for the given ship, lazily building it if absent.
   *
   * @param ship the ship to index
   * @return the top-surface index for the ship
   */
  /**
   * @param ship ship whose stored pose basis is needed
   * @return the last committed pose basis, or a zero pose when none is stored
   */
  private ShipPose poseBasis(Ship ship) {
    return poseBasisByShip.getOrDefault(ship.id(), new ShipPose(0));
  }

  private TopSurfaceIndex indexFor(Ship ship) {
    return indices.computeIfAbsent(
        ship.id(), k -> TopSurfaceIndex.build(CollisionHull.topExposedBlocks(ship), ship));
  }

  /**
   * Returns the bounding box of an entity positioned at the given location.
   *
   * @param entity the entity whose size determines the box
   * @param location the location where the entity is standing
   * @return the bounding box at that location
   */
  private BoundingBox boundsAt(Entity entity, Location location) {
    double halfWidth = entity.getWidth() / 2.0;
    double x = location.getX();
    double y = location.getY();
    double z = location.getZ();
    return new BoundingBox(
        x - halfWidth, y, z - halfWidth, x + halfWidth, y + entity.getHeight(), z + halfWidth);
  }

  /**
   * Returns whether the entity may be considered as a ship rider.
   *
   * @param entity the entity to evaluate
   * @return true when the entity may ride a ship
   */
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

  /**
   * Returns whether the entity is owned by a ship's collision or render volume.
   *
   * @param entity the entity to inspect
   * @return true when the entity carries a ship-owned persistent key
   */
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

  /**
   * Adds the entity to the ship's rider set and updates the reverse lookup.
   *
   * @param shipId the ship the entity is now riding
   * @param entityId the id of the riding entity
   */
  private void addRider(UUID shipId, UUID entityId) {
    ridersByShip.computeIfAbsent(shipId, k -> new HashSet<>()).add(entityId);
    shipByEntity.put(entityId, shipId);
  }

  /**
   * Removes the entity from whichever ship it is currently riding, if any.
   *
   * @param entityId the id of the entity to remove
   */
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
