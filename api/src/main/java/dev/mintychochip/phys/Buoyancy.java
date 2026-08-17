package dev.mintychochip.phys;

import dev.mintychochip.archimedes.model.Ship;

/** Buoyancy operations the ship service depends on. */
public interface Buoyancy {
  /**
   * Floats the ship up to its equilibrium waterline.
   *
   * @param ship the ship
   * @return false when the path is blocked
   */
  boolean rise(Ship ship);

  /**
   * Integrates one physics tick for the ship.
   *
   * @param ship the ship
   * @return true when the ship moved this tick
   */
  boolean tick(Ship ship);

  /**
   * Manually lowers the ship.
   *
   * @param ship the ship
   * @param blocks the number of blocks to lower
   * @return false when the path is blocked
   */
  boolean sink(Ship ship, int blocks);

  /**
   * Clears per-ship runtime state.
   *
   * @param ship the ship
   */
  void clear(Ship ship);
}
