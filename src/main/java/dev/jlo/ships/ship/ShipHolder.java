package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;

/** Receives the finalized ship model after rendering. */
@FunctionalInterface
public interface ShipHolder {
  /**
   * Accepts the finalized ship.
   *
   * @param ship the finalized ship
   */
  void accept(Ship ship);
}
