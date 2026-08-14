package dev.jlo.ships.collision;

import dev.jlo.ships.model.Ship;
import java.util.UUID;

/** Owner of temporary collision volumes for persisted ships. */
public interface CollisionVolumeManager {
  /** Spawns or reconciles every exposed volume for a ship. */
  void spawn(Ship ship);

  /** Moves every volume for a ship to its current transformed cells. */
  void move(Ship ship);

  /** Removes all collision volumes owned by a ship. */
  void remove(UUID shipId);

  /** Removes all tracked collision volumes. */
  void removeAll();
}
