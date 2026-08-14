package dev.jlo.ships.bukkit;

import dev.jlo.ships.collision.CollisionHull;
import dev.jlo.ships.collision.CollisionVolume;
import dev.jlo.ships.collision.CollisionVolumeManager;
import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipTransform;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.bukkit.persistence.PersistentDataType;

/** Bukkit collision manager using non-persistent Shulkers for exposed ship blocks. */
public final class BukkitCollisionVolumeManager implements CollisionVolumeManager {
  private final World world;
  private final NamespacedKey ownerKey;
  private final NamespacedKey blockKey;
  private final Map<UUID, Map<BlockPos, CollisionVolume>> volumes = new HashMap<>();

  /** Creates a manager. */
  public BukkitCollisionVolumeManager(World world, NamespacedKey ownerKey) {
    this.world = world;
    this.ownerKey = ownerKey;
    this.blockKey = new NamespacedKey(ownerKey.getNamespace(), ownerKey.getKey() + "-block");
  }

  @Override
  public void spawn(Ship ship) {
    remove(ship.id());
    Map<BlockPos, CollisionVolume> spawned = new HashMap<>();
    try {
      for (BlockPos relative : CollisionHull.exposedBlocks(ship)) {
        BlockPos cell = ShipTransform.cell(ship, relative);
        Shulker shulker =
            world.spawn(
                new Location(world, cell.x() + 0.5, cell.y(), cell.z() + 0.5),
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
      for (CollisionVolume volume : spawned.values()) {
        volume.remove();
      }
      throw failure;
    }
  }

  @Override
  public void move(Ship ship) {
    Map<BlockPos, CollisionVolume> shipVolumes = volumes.get(ship.id());
    if (shipVolumes == null) {
      spawn(ship);
      return;
    }
    Map<CollisionVolume, BlockPos> previous = new HashMap<>();
    try {
      for (Map.Entry<BlockPos, CollisionVolume> entry : shipVolumes.entrySet()) {
        CollisionVolume volume = entry.getValue();
        Location location = ((BukkitShulkerCollisionVolume) volume).entity.getLocation();
        previous.put(
            volume, new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
        BlockPos cell = ShipTransform.cell(ship, entry.getKey());
        volume.move(cell.x(), cell.y(), cell.z());
      }
    } catch (RuntimeException failure) {
      for (Map.Entry<CollisionVolume, BlockPos> entry : previous.entrySet()) {
        BlockPos cell = entry.getValue();
        try {
          entry.getKey().move(cell.x(), cell.y(), cell.z());
        } catch (RuntimeException cleanup) {
          failure.addSuppressed(cleanup);
        }
      }
      throw failure;
    }
  }

  @Override
  public void rollback(Ship ship, double oldY) {
    Map<BlockPos, CollisionVolume> shipVolumes = volumes.get(ship.id());
    if (shipVolumes == null) {
      return;
    }
    for (Map.Entry<BlockPos, CollisionVolume> entry : shipVolumes.entrySet()) {
      BlockPos cell = ShipTransform.cell(ship, entry.getKey());
      int oldAnchor = (int) Math.floor(oldY);
      entry.getValue().move(cell.x(), cell.y() - ship.pose().anchorDy() + oldAnchor, cell.z());
    }
  }

  @Override
  public void remove(UUID shipId) {
    Map<BlockPos, CollisionVolume> shipVolumes = volumes.remove(shipId);
    if (shipVolumes != null) {
      for (CollisionVolume volume : shipVolumes.values()) {
        volume.remove();
      }
    }
  }

  @Override
  public void removeAll() {
    for (UUID shipId : java.util.Set.copyOf(volumes.keySet())) {
      remove(shipId);
    }
  }

  private static String key(BlockPos position) {
    return position.x() + "," + position.y() + "," + position.z();
  }

  private static final class BukkitShulkerCollisionVolume implements CollisionVolume {
    private final UUID shipId;
    private final Shulker entity;

    private BukkitShulkerCollisionVolume(UUID shipId, Shulker entity) {
      this.shipId = shipId;
      this.entity = entity;
    }

    @Override
    public UUID shipId() {
      return shipId;
    }

    @Override
    public void move(int x, int y, int z) {
      Location location = entity.getLocation();
      entity.teleport(
          new Location(
              location.getWorld(), x + 0.5, y, z + 0.5, location.getYaw(), location.getPitch()));
    }

    @Override
    public void remove() {
      entity.remove();
    }
  }
}
