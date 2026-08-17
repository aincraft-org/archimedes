package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Ship;
import java.util.Map;
import java.util.UUID;

/** Persistence backend the ship service depends on. */
public interface ShipStoreLike {
  /**
   * Loads all persisted ships.
   *
   * @return all persisted ships keyed by identifier
   */
  Map<UUID, Ship> loadAll();

  /**
   * Saves all ships.
   *
   * @param ships the ships to save
   */
  void saveAll(Map<UUID, Ship> ships);
}
