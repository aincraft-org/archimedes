package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;

/**
 * Carries non-ship entities that are standing on a ship so they move with it.
 *
 * <p>Implementations are best-effort: they should not throw exceptions for expected Bukkit failures
 * such as an entity leaving the world or a teleport returning false.
 */
public interface ShipEntityCarrier {
  /**
   * Starts tracking a ship and seeds riders using the supplied committed pose.
   *
   * @param ship ship to track
   * @param poseY committed pose used for initial overlap checks
   */
  default void track(Ship ship, double poseY) {}

  /**
   * Stops tracking a ship and drops its rider state.
   *
   * @param ship ship to untrack
   */
  default void untrack(Ship ship) {}

  /** Clears all tracked ships and riders. */
  default void clear() {}

  /**
   * Carries eligible entities on the ship by the same vertical delta.
   *
   * @param ship ship being moved
   * @param oldY old pose
   * @param newY new pose
   */
  void carry(Ship ship, double oldY, double newY);
}
