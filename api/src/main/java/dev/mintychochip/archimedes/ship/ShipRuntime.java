package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.Collection;

/** Composes visual and collision runtime lifecycle for a ship. */
public interface ShipRuntime {
  /**
   * Spawns all runtime components.
   *
   * @param ship ship to spawn
   */
  void spawn(Vehicle ship);

  /**
   * Moves all runtime components from one pose to another.
   *
   * @param ship ship to move
   * @param oldY previous pose y
   * @param newY new pose y
   */
  void move(Vehicle ship, double oldY, double newY);

  /**
   * Moves all runtime components from one pose to another.
   *
   * @param ship ship to move
   * @param from previous pose
   * @param to new pose
   */
  default void move(Vehicle ship, ShipPose from, ShipPose to) {
    move(ship, from.y(), to.y());
  }

  /**
   * Removes all runtime components for one ship.
   *
   * @param ship ship to remove
   */
  void remove(Vehicle ship);

  /**
   * Removes all runtime components for a collection of ships.
   *
   * @param ships ships to remove
   */
  void removeAll(Collection<Vehicle> ships);

  /** Removes stale plugin-owned runtime entities not represented by models. */
  default void removeAllTagged() {}

  /**
   * Spawns a torn cloth cell as a ragdolling block display.
   *
   * @param ship parent vehicle
   * @param debrisId stable debris id
   * @param appearance captured block data
   * @param x world x
   * @param y world y
   * @param z world z
   */
  default void spawnClothRagdoll(
      Vehicle ship, java.util.UUID debrisId, String appearance, double x, double y, double z) {}

  /**
   * Teleports an existing cloth ragdoll, including orientation.
   *
   * @param debrisId debris id
   * @param x world x
   * @param y world y
   * @param z world z
   * @param qx quaternion x
   * @param qy quaternion y
   * @param qz quaternion z
   * @param qw quaternion w
   */
  default void moveClothRagdoll(
      java.util.UUID debrisId,
      double x,
      double y,
      double z,
      double qx,
      double qy,
      double qz,
      double qw) {}
}
