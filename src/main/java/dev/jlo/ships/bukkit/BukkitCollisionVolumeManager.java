package dev.jlo.ships.bukkit;

import dev.jlo.ships.collision.CollisionHull;
import dev.jlo.ships.collision.CollisionVolume;
import dev.jlo.ships.collision.CollisionVolumeManager;
import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipTransform;
import dev.jlo.ships.ship.ShipRuntimeException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.bukkit.persistence.PersistentDataType;

/** Bukkit collision manager using non-persistent Shulkers for exposed ship blocks. */
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
                  entity.addScoreboardTag("ships-collision-" + ship.id());
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

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
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
        previous.put(
            volume,
            new ShipTransform.CollisionAnchor(location.getX(), location.getY(), location.getZ()));
        ShipTransform.CollisionAnchor anchor = ShipTransform.collisionAnchor(ship, entry.getKey());
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

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
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

  @Override
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

  @Override
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
  public void removeAllTagged() {
    removeAll();
  }

  private static String key(BlockPos position) {
    return position.x() + "," + position.y() + "," + position.z();
  }

  /** Collision volume backed by a Bukkit Shulker entity. */
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
