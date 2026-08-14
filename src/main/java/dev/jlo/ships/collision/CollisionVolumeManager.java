package dev.jlo.ships.collision;

import java.util.UUID;
import org.bukkit.Location;

/** Factory and owner-scoped cleanup for temporary ship collision volumes. */
public interface CollisionVolumeManager {
  /** Spawns a collision volume at a world location. */
  /**
   * @param shipId owning ship identifier
   * @param location spawn location
   * @return the spawned collision volume
   */
  CollisionVolume spawn(UUID shipId, Location location);

  /**
   * Removes all collision volumes owned by a ship.
   *
   * @param shipId owning ship identifier
   */
  void remove(UUID shipId);
}
