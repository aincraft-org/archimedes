package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;
import java.util.Collection;

/** Composes visual and collision runtime lifecycle for a ship. */
public interface ShipRuntime {
  /** Spawns all runtime components. */
  void spawn(Ship ship);

  /** Moves all runtime components from one pose to another. */
  void move(Ship ship, double oldY, double newY);

  /** Removes all runtime components for one ship. */
  void remove(Ship ship);

  /** Removes all runtime components for a collection of ships. */
  void removeAll(Collection<Ship> ships);
}
