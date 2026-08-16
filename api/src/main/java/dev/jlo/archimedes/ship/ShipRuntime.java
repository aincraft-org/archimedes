package dev.jlo.archimedes.ship;

import dev.jlo.archimedes.model.Ship;
import java.util.Collection;

/** Composes visual and collision runtime lifecycle for a ship. */
public interface ShipRuntime {
  /**
   * Spawns all runtime components.
   *
   * @param ship ship to spawn
   */
  void spawn(Ship ship);

  /**
   * Moves all runtime components from one pose to another.
   *
   * @param ship ship to move
   * @param oldY previous pose y
   * @param newY new pose y
   */
  void move(Ship ship, double oldY, double newY);

  /**
   * Removes all runtime components for one ship.
   *
   * @param ship ship to remove
   */
  void remove(Ship ship);

  /**
   * Removes all runtime components for a collection of ships.
   *
   * @param ships ships to remove
   */
  void removeAll(Collection<Ship> ships);

  /** Removes stale plugin-owned runtime entities not represented by models. */
  default void removeAllTagged() {}
}
