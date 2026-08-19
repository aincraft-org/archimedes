package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Vehicle;
import java.util.Map;
import java.util.UUID;

/** Persistence backend the ship service depends on. */
public interface ShipStoreLike {
  /**
   * Loads all persisted ships.
   *
   * @return all persisted ships keyed by identifier
   */
  Map<UUID, Vehicle> loadAll();

  /**
   * Saves all ships.
   *
   * @param ships the ships to save
   */
  void saveAll(Map<UUID, Vehicle> ships);
}
