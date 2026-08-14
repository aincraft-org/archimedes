package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;
import java.util.Map;
import java.util.UUID;

/** Persistence backend the ship service depends on. */
public interface ShipStoreLike {
  /** Loads all persisted ships. */
  Map<UUID, Ship> loadAll();

  /** Saves all ships. */
  void saveAll(Map<UUID, Ship> ships);
}