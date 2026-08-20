package dev.mintychochip.archimedes.collision;

import dev.mintychochip.archimedes.model.Vehicle;
import java.util.Collection;
import java.util.UUID;

/** Owner of temporary collision volumes for persisted ships. */
public interface CollisionVolumeManager {
  /**
   * Spawns or reconciles every exposed volume for a ship.
   *
   * @param ship ship whose volumes are spawned
   */
  void spawn(Vehicle ship);

  /**
   * Moves every volume for a ship to its current transformed cells.
   *
   * @param ship ship whose volumes move
   */
  void move(Vehicle ship);

  /**
   * Restores every volume for a ship to its previous integer anchor.
   *
   * @param ship ship whose volumes roll back
   * @param oldY previous pose y
   */
  void rollback(Vehicle ship, double oldY);

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

  /**
   * Returns the spawn policy for {@code shipId}, defaulting to streamed.
   *
   * @param shipId ship identifier
   * @return current mode
   */
  default CollisionMode mode(UUID shipId) {
    return CollisionMode.STREAMED;
  }

  /**
   * Sets the spawn policy for {@code ship} and applies it to live volumes when present.
   *
   * @param ship ship whose mode is set
   * @param mode spawn policy
   */
  default void setMode(Vehicle ship, CollisionMode mode) {}

  /**
   * Returns occupancy counts for inspect and A/B comparison.
   *
   * @param shipId ship identifier
   * @param playerId player whose visible count is reported
   * @return snapshot of mode and counts
   */
  default CollisionSnapshot snapshot(UUID shipId, UUID playerId) {
    return new CollisionSnapshot(CollisionMode.STREAMED, 0, 0, 0);
  }

  /**
   * Reconciles streamed volumes against the supplied observers. Ignored in full mode.
   *
   * @param ship ship whose volumes are reconciled
   * @param observers nearby entities that may need hull cubes
   */
  default void observe(Vehicle ship, Collection<CollisionObserver> observers) {}
}
