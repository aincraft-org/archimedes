package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Vehicle;

/** Renderer backend the ship service depends on. */
public interface ShipRendererLike {
  /**
   * Renders a ship, passing the finalized model to the holder.
   *
   * @param ship the ship to render
   * @param holder the finalization receiver
   */
  void render(Vehicle ship, ShipHolder holder);

  /**
   * Removes all runtime entities for a ship.
   *
   * @param ship the ship to clean up
   */
  void removeRuntime(Vehicle ship);

  /**
   * Repositions an already-rendered ship's displays from one pose to another.
   *
   * @param ship the ship to reposition
   * @param oldY the previous pose y
   * @param newY the new pose y
   */
  void reposition(Vehicle ship, double oldY, double newY);

  /** Removes every plugin-owned runtime display, including stale entities. */
  default void removeAllRuntime() {}

  /**
   * Spawns a torn cloth ragdoll display.
   *
   * @param ship parent vehicle
   * @param debrisId debris id
   * @param appearance captured block data
   * @param x world x
   * @param y world y
   * @param z world z
   */
  default void spawnClothRagdoll(
      Vehicle ship, java.util.UUID debrisId, String appearance, double x, double y, double z) {}

  /**
   * Moves a torn cloth ragdoll display.
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
