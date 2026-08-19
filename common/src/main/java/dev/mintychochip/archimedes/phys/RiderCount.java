package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.Vehicle;

/** Supplies the number of players whose mass is included in a ship body. */
public interface RiderCount {
  /**
   * Counts tracked riders for a ship.
   *
   * @param ship ship whose runtime riders are counted
   * @return a non-negative rider count
   */
  int count(Vehicle ship);
}
