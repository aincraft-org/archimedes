package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.Vehicle;

/**
 * Controls buoyancy-driven vertical movement for ships.
 *
 * <p>Boolean operations report whether movement occurred or was accepted; a disabled ship is
 * handled according to each operation's lifecycle semantics.
 */
public interface ShipPhysics {
  /**
   * Advances buoyancy simulation for one ship tick.
   *
   * @param ship ship to update
   * @return whether the ship moved
   */
  boolean tick(Vehicle ship);

  /**
   * Steps the ship through the physics engine until vertical motion settles.
   *
   * @param ship ship to raise
   * @return whether the request succeeded
   */
  boolean rise(Vehicle ship);

  /**
   * Moves the ship downward by up to the requested number of blocks.
   *
   * @param ship ship to sink
   * @param blocks positive downward distance
   * @return whether the path was clear and movement succeeded
   */
  boolean sink(Vehicle ship, int blocks);

  /**
   * Clears per-ship velocity state.
   *
   * @param ship ship whose transient physics state is removed
   */
  void clear(Vehicle ship);

  /**
   * Samples pose, mass factors, last-tick cost, wind, and each attached force without moving the
   * ship.
   *
   * @param ship ship to inspect
   * @return diagnostic snapshot
   */
  ShipInspection inspect(Vehicle ship);
}
