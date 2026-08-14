package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;

/** Renderer backend the ship service depends on. */
public interface ShipRendererLike {
  /**
   * Renders a ship, passing the finalized model to the holder.
   *
   * @param ship the ship to render
   * @param holder the finalization receiver
   */
  void render(Ship ship, ShipHolder holder);

  /**
   * Removes all runtime entities for a ship.
   *
   * @param ship the ship to clean up
   */
  void removeRuntime(Ship ship);
}
