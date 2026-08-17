package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.ship.ShipStoreLike;
import dev.mintychochip.archimedes.store.ShipStore;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Adapts the JSON {@link ShipStore} to the service's store contract. */
public final class BukkitShipStore implements ShipStoreLike {
  /** The underlying JSON store. */
  private final ShipStore store;

  /**
   * Creates the adapter around a JSON store.
   *
   * @param store the JSON store
   */
  public BukkitShipStore(ShipStore store) {
    this.store = store;
  }

  /**
   * Loads all persisted ships.
   *
   * @return all persisted ships keyed by identifier
   */
  @Override
  public Map<UUID, Ship> loadAll() {
    try {
      return store.loadAll();
    } catch (IOException failure) {
      throw new IllegalStateException("Failed to load Archimedes", failure);
    }
  }

  /**
   * Saves all ships.
   *
   * @param ships the ships to save
   */
  @Override
  public void saveAll(Map<UUID, Ship> ships) {
    try {
      store.saveAll(ships);
    } catch (IOException failure) {
      throw new IllegalStateException("Failed to save Archimedes", failure);
    }
  }
}
