package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipPose;

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
   * Starts tracking a ship and seeds riders using the supplied committed pose.
   *
   * @param ship ship to track
   * @param pose committed pose used for initial overlap checks
   */
  default void track(Ship ship, ShipPose pose) {
    track(ship, pose.y());
  }

  /**
   * Stops tracking a ship and drops its rider state.
   *
   * @param ship ship to untrack
   */
  default void untrack(Ship ship) {}

  /**
   * Updates the stored pose basis after a committed or rolled-back runtime move.
   *
   * @param ship ship whose basis is updated
   * @param poseY pose basis to store
   */
  default void updatePoseBasis(Ship ship, double poseY) {}

  /**
   * Updates the stored pose basis after a committed or rolled-back runtime move.
   *
   * @param ship ship whose basis is updated
   * @param pose pose basis to store
   */
  default void updatePoseBasis(Ship ship, ShipPose pose) {
    updatePoseBasis(ship, pose.y());
  }

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

  /**
   * Carries eligible entities on the ship by the same pose delta.
   *
   * @param ship ship being moved
   * @param from previous pose
   * @param to new pose
   */
  default void carry(Ship ship, ShipPose from, ShipPose to) {
    carry(ship, from.y(), to.y());
  }
}
