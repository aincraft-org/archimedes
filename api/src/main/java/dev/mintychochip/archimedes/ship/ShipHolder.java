package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Vehicle;

/** Receives the finalized ship model after rendering. */
@FunctionalInterface
public interface ShipHolder {
  /**
   * Accepts the finalized ship.
   *
   * @param ship the finalized ship
   */
  void accept(Vehicle ship);
}
