package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.collision.CollisionHull;
import dev.mintychochip.archimedes.collision.CollisionVolume;
import dev.mintychochip.archimedes.collision.CollisionVolumeManager;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipTransform;
import dev.mintychochip.archimedes.ship.ShipRuntimeException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages non-persistent Shulkers that represent exposed ship blocks as Bukkit collision volumes.
 *
 * <p>Volumes are indexed in memory by ship id and relative block position. Spawn and move failures
 * attempt cleanup or rollback and attach cleanup failures as suppressed exceptions.
 */
@SuppressWarnings({
  "checkstyle:IllegalCatch",
  "PMD.AvoidCatchingGenericException",
  "PMD.AvoidDuplicateLiterals"
})
public final class BukkitCollisionVolumeManager implements CollisionVolumeManager {
  /** Bukkit world containing collision entities. */
  private final World world;

  /** Persistent key identifying plugin-owned collision entities. */
  private final NamespacedKey ownerKey;

  /** Persistent key identifying the relative block represented by an entity. */
  private final NamespacedKey blockKey;

  /** Collision volumes indexed by ship and relative block position. */
  private final Map<UUID, Map<BlockPos, CollisionVolume>> volumes = new HashMap<>();

  /**
   * Creates a manager for the supplied Bukkit world and owner key.
   *
   * @param world Bukkit world containing collision entities
   * @param ownerKey persistent key identifying plugin-owned entities
   */
  public BukkitCollisionVolumeManager(World world, NamespacedKey ownerKey) {
    this.world = world;
    this.ownerKey = ownerKey;
    this.blockKey = new NamespacedKey(ownerKey.getNamespace(), ownerKey.getKey() + "-block");
  }

  /**
   * Spawns one collision volume for each exposed relative block. Existing volumes for the ship are
   * removed first; a partial spawn is cleaned up before the normalized failure is rethrown.
   *
   * @param ship ship whose exposed blocks receive collision volumes
   */
  @Override
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public void spawn(Ship ship) {
    normalizeRemoval(ship.id(), "collision spawn pre-cleanup");

    Map<BlockPos, CollisionVolume> spawned = new HashMap<>();
    try {
      for (BlockPos relative : CollisionHull.exposedBlocks(ship)) {
        ShipTransform.CollisionAnchor anchor = ShipTransform.collisionAnchor(ship, relative);
        Shulker shulker =
            world.spawn(
                new Location(world, anchor.x(), anchor.y(), anchor.z()),
                Shulker.class,
                entity -> {
                  entity.setAI(false);
                  entity.setInvisible(true);
                  entity.setInvulnerable(true);
                  entity.setSilent(true);
                  entity.setGravity(false);
                  entity.setCollidable(true);
                  entity.setPeek(0.0f);
                  entity.setPersistent(false);
                  entity
                      .getPersistentDataContainer()
                      .set(ownerKey, PersistentDataType.STRING, ship.id().toString());
                  entity
                      .getPersistentDataContainer()
                      .set(blockKey, PersistentDataType.STRING, key(relative));
                  entity.addScoreboardTag("archimedes-collision-" + ship.id());
                });
        spawned.put(relative, new BukkitShulkerCollisionVolume(ship.id(), shulker));
      }
      volumes.put(ship.id(), spawned);
    } catch (RuntimeException failure) {
      ShipRuntimeException normalized =
          failure instanceof ShipRuntimeException
              ? (ShipRuntimeException) failure
              : new ShipRuntimeException(
                  "Bukkit collision spawn failed for ship " + ship.id(), failure);
      cleanupSpawned(spawned, normalized);
      throw normalized;
    }
  }

  /**
   * Removes all partially spawned volumes and attaches cleanup failures to the original failure.
   *
   * @param spawned partially created volumes
   * @param failure original spawn failure receiving suppressed cleanup failures
   */
  private static void cleanupSpawned(
      Map<BlockPos, CollisionVolume> spawned, ShipRuntimeException failure) {
    for (CollisionVolume volume : spawned.values()) {
      try {
        volume.remove();
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
    }
  }

  /**
   * Moves registered volumes to their model collision anchors. Only changes in the anchor's block
   * y-coordinate are applied; if a later move fails, earlier moves are restored to their prior
   * locations.
   *
   * @param ship ship whose registered volumes are moved
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  @Override
  public void move(Ship ship) {
    Map<BlockPos, CollisionVolume> shipVolumes = volumes.get(ship.id());
    if (shipVolumes == null) {
      spawn(ship);
      return;
    }
    Map<CollisionVolume, ShipTransform.CollisionAnchor> previous = new HashMap<>();
    try {
      for (Map.Entry<BlockPos, CollisionVolume> entry : shipVolumes.entrySet()) {
        CollisionVolume volume = entry.getValue();
        Location location = ((BukkitShulkerCollisionVolume) volume).entity.getLocation();
        ShipTransform.CollisionAnchor oldAnchor =
            new ShipTransform.CollisionAnchor(location.getX(), location.getY(), location.getZ());
        ShipTransform.CollisionAnchor anchor = ShipTransform.collisionAnchor(ship, entry.getKey());
        if (Math.floor(oldAnchor.y()) == Math.floor(anchor.y())) {
          continue;
        }
        previous.put(volume, oldAnchor);
        volume.move(anchor.x(), anchor.y(), anchor.z());
      }
    } catch (ShipRuntimeException failure) {
      rollbackMoved(previous, failure);
      throw failure;
    } catch (RuntimeException failure) {
      ShipRuntimeException wrapped =
          new ShipRuntimeException(
              new IllegalStateException("Collision move failed for ship " + ship.id(), failure));
      rollbackMoved(previous, wrapped);
      throw wrapped;
    }
  }

  /**
   * Restores previously moved volumes after a failed move, preserving the original failure.
   *
   * @param previous volumes and their prior absolute anchors
   * @param failure original move failure receiving suppressed rollback failures
   */
  private void rollbackMoved(
      Map<CollisionVolume, ShipTransform.CollisionAnchor> previous, ShipRuntimeException failure) {
    for (Map.Entry<CollisionVolume, ShipTransform.CollisionAnchor> entry : previous.entrySet()) {
      ShipTransform.CollisionAnchor anchor = entry.getValue();
      try {
        entry.getKey().move(anchor.x(), anchor.y(), anchor.z());
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
    }
  }

  /**
   * Restores registered volumes to a prior pose y without changing the ship model.
   *
   * @param ship ship whose volumes are restored
   * @param oldY previous pose y to restore
   */
  public void rollback(Ship ship, double oldY) {
    Map<BlockPos, CollisionVolume> shipVolumes = volumes.get(ship.id());
    if (shipVolumes == null) {
      return;
    }
    for (Map.Entry<BlockPos, CollisionVolume> entry : shipVolumes.entrySet()) {
      ShipTransform.CollisionAnchor current = ShipTransform.collisionAnchor(ship, entry.getKey());
      double delta = oldY - ship.pose().y();
      entry.getValue().move(current.x(), current.y() + delta, current.z());
    }
  }

  /**
   * Removes all tracked volumes for one ship and reports aggregated cleanup failures.
   *
   * @param shipId identifier of the ship to remove
   */
  public void remove(UUID shipId) {
    normalizeRemoval(shipId, "collision removal");
  }

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private void normalizeRemoval(UUID shipId, String operation) {
    Map<BlockPos, CollisionVolume> shipVolumes = volumes.remove(shipId);
    if (shipVolumes == null) {
      return;
    }
    ShipRuntimeException failure = null;
    for (CollisionVolume volume : shipVolumes.values()) {
      try {
        volume.remove();
      } catch (RuntimeException cleanup) {
        ShipRuntimeException normalized =
            cleanup instanceof ShipRuntimeException
                ? (ShipRuntimeException) cleanup
                : new ShipRuntimeException(operation + " failed for ship " + shipId, cleanup);
        if (failure == null) {
          failure = normalized;
        } else {
          failure.addSuppressed(normalized);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private void removeOneTagged(Shulker shulker, String operation) {
    try {
      shulker.remove();
    } catch (RuntimeException failure) {
      if (failure instanceof ShipRuntimeException) {
        throw (ShipRuntimeException) failure;
      }
      throw new ShipRuntimeException(operation, failure);
    }
  }

  private ShipRuntimeException normalizeFailure(
      String operation, UUID shipId, RuntimeException failure) {
    if (failure instanceof ShipRuntimeException) {
      return (ShipRuntimeException) failure;
    }
    return new ShipRuntimeException(operation + " failed for ship " + shipId, failure);
  }

  /**
   * Removes all plugin-owned collision volumes and any matching stale Shulker entities in the
   * world. Removal continues across individual failures so that all candidates are attempted;
   * failures are aggregated and reported after the sweep.
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  @Override
  public void removeAll() {
    ShipRuntimeException failure = null;
    try {
      for (Shulker shulker : java.util.List.copyOf(world.getEntitiesByClass(Shulker.class))) {
        try {
          if (shulker.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) {
            UUID shipId = parseShipId(shulker);
            removeOneTagged(
                shulker,
                "collision tagged removal failed" + (shipId == null ? "" : " for ship " + shipId));
          }
        } catch (RuntimeException cleanup) {
          failure = aggregateRemoval(failure, cleanup);
        }
      }
    } catch (RuntimeException current) {
      failure = aggregateRemoval(failure, current);
    }
    for (UUID shipId : java.util.Set.copyOf(volumes.keySet())) {
      try {
        normalizeRemoval(shipId, "collision removal");
      } catch (RuntimeException cleanup) {
        ShipRuntimeException normalized = normalizeFailure("collision removal", shipId, cleanup);
        if (failure == null) {
          failure = normalized;
        } else {
          failure.addSuppressed(normalized);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static ShipRuntimeException aggregateRemoval(
      ShipRuntimeException failure, RuntimeException current) {
    ShipRuntimeException wrapped =
        current instanceof ShipRuntimeException runtime
            ? runtime
            : new ShipRuntimeException(current);
    if (failure == null) {
      return wrapped;
    }
    failure.addSuppressed(wrapped);
    return failure;
  }

  private UUID parseShipId(Shulker shulker) {
    String value = shulker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  /** Removes every plugin-owned collision entity, including stale entities. */
  @Override
  public void removeAllTagged() {
    removeAll();
  }

  private static String key(BlockPos position) {
    return position.x() + "," + position.y() + "," + position.z();
  }

  /** Collision volume backed by a Bukkit Shulker entity; movement preserves its ship identity. */
  private static final class BukkitShulkerCollisionVolume implements CollisionVolume {
    /** Owning ship identifier. */
    private final UUID shipId;

    /** Backing Bukkit entity. */
    private final Shulker entity;

    private BukkitShulkerCollisionVolume(UUID shipId, Shulker entity) {
      this.shipId = shipId;
      this.entity = entity;
    }

    @Override
    public UUID shipId() {
      return shipId;
    }

    /**
     * Moves the backing entity to an absolute world coordinate, preserving orientation.
     *
     * @param x absolute world x coordinate
     * @param y absolute world y coordinate
     * @param z absolute world z coordinate
     */
    public void move(double x, double y, double z) {
      try {
        Location location = entity.getLocation();
        if (!entity.teleport(
            new Location(location.getWorld(), x, y, z, location.getYaw(), location.getPitch()))) {
          throw new ShipRuntimeException(
              new IllegalStateException("Collision entity teleport returned false"));
        }
      } catch (ShipRuntimeException failure) {
        throw failure;
      } catch (RuntimeException failure) {
        throw new ShipRuntimeException(
            new IllegalStateException(
                "Collision move teleport failed for ship " + shipId, failure));
      }
    }

    /** Removes the backing entity, normalizing Bukkit failures to {@link ShipRuntimeException}. */
    @Override
    public void remove() {
      try {
        entity.remove();
      } catch (IllegalArgumentException failure) {
        throw new ShipRuntimeException(
            new IllegalArgumentException(
                "Failed to remove collision volume for ship " + shipId, failure));
      }
    }
  }
}
