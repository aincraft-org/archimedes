package dev.jlo.ships.bukkit;

import dev.jlo.ships.collision.CollisionVolume;
import dev.jlo.ships.collision.CollisionVolumeManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.bukkit.persistence.PersistentDataType;

/** Bukkit prototype manager using invisible Shulkers as collision volumes. */
public final class BukkitCollisionVolumeManager implements CollisionVolumeManager {
  /** World containing collision entities. */
  private final World world;

  /** Persistent ownership key. */
  private final NamespacedKey ownerKey;

  /** Active volumes keyed by owner. */
  private final Map<UUID, CollisionVolume> volumes = new HashMap<>();

  /**
   * Creates a manager.
   *
   * @param world world containing collision entities
   * @param ownerKey persistent ownership key
   */
  public BukkitCollisionVolumeManager(World world, NamespacedKey ownerKey) {
    this.world = world;
    this.ownerKey = ownerKey;
  }

  @Override
  public CollisionVolume spawn(UUID shipId, Location location) {
    Shulker shulker =
        world.spawn(
            location,
            Shulker.class,
            entity -> {
              entity.setAI(false);
              entity.setInvisible(true);
              entity.setInvulnerable(true);
              entity.setSilent(true);
              entity.setGravity(false);
              entity.setCollidable(true);
              entity.setPeek(0.0f);
              entity
                  .getPersistentDataContainer()
                  .set(ownerKey, PersistentDataType.STRING, shipId.toString());
              entity.addScoreboardTag("ships-collision-" + shipId);
            });
    CollisionVolume volume = new BukkitShulkerCollisionVolume(shipId, shulker);
    volumes.put(shipId, volume);
    return volume;
  }

  @Override
  public void remove(UUID shipId) {
    CollisionVolume volume = volumes.remove(shipId);
    if (volume != null) {
      volume.remove();
    }
  }

  /** Bukkit-backed collision volume. */
  private static final class BukkitShulkerCollisionVolume implements CollisionVolume {
    /** Owning ship identifier. */
    private final UUID shipId;

    /** Collision entity. */
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
