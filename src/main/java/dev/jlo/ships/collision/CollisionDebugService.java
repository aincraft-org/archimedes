package dev.jlo.ships.collision;

import java.util.UUID;

/** Debug-only lifecycle for the non-block collision spike. */
public interface CollisionDebugService {
  /**
   * Spawns a one-volume test fixture for a player.
   *
   * @param playerId player identifier
   * @param x world x coordinate
   * @param y world y coordinate
   * @param z world z coordinate
   * @return spawned collision volume
   */
  CollisionVolume spawn(UUID playerId, int x, int y, int z);

  /**
   * Moves a player's current test fixture by one block offset.
   *
   * @param playerId player identifier
   * @param dy vertical offset
   * @return true when a fixture exists
   */
  boolean move(UUID playerId, int dy);

  /**
   * Removes a player's current test fixture.
   *
   * @param playerId player identifier
   * @return true when a fixture existed
   */
  boolean remove(UUID playerId);

  /** Removes every debug fixture. */
  void removeAll();
}
