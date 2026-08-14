package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;

/** Renderer backend the ship service depends on. */
public interface ShipRendererLike {
  /** Renders a ship, passing the finalized model to the holder. */
  void render(Ship ship, ShipHolder holder);

  /** Removes all runtime entities for a ship. */
  void removeRuntime(Ship ship);
}