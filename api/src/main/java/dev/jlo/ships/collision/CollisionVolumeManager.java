package dev.jlo.ships.collision;

import dev.jlo.ships.model.Ship;
import java.util.UUID;

/** Owner of temporary collision volumes for persisted ships. */
public interface CollisionVolumeManager {
  /**
   * Spawns or reconciles every exposed volume for a ship.
   *
   * @param ship ship whose volumes are spawned
   */
  void spawn(Ship ship);

  /**
   * Moves every volume for a ship to its current transformed cells.
   *
   * @param ship ship whose volumes move
   */
  void move(Ship ship);

  /**
   * Restores every volume for a ship to its previous integer anchor.
   *
   * @param ship ship whose volumes roll back
   * @param oldY previous pose y
   */
  void rollback(Ship ship, double oldY);

  /**
   * Removes all collision volumes owned by a ship.
   *
   * @param shipId ship identifier
   */
  void remove(UUID shipId);

  /** Removes all tracked collision volumes. */
  void removeAll();

  /** Removes every plugin-owned collision entity, including stale entities. */
  default void removeAllTagged() {}
}
