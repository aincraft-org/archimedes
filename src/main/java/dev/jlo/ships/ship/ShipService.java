package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the ship registry and runtime lifecycle for assembly, lookup, and
 * disassembly. Backed by a persistence store and renderer.
 */
public interface ShipService {
  /** Assembles a ship at the given seed, returning it or null on failure. */
  Ship assembleAt(UUID playerId, int x, int y, int z, UUID worldId);

  /** Returns the ship owned by a player in their current world, or null. */
  Ship findOwnedInWorld(UUID playerId, UUID worldId);

  /** Disassembles a ship, returning true on success. */
  boolean disassemble(UUID shipId, UUID requesterId, boolean operator);

  /** Returns the last operation failure message. */
  String lastError();

  /** Loads all persisted ships into the registry. */
  Map<UUID, Ship> loadAll();

  /** Persists all registered ships. */
  void saveAll();

  /** Removes all runtime entities and barriers for registered ships. */
  void removeAllRuntime();

  /** Returns every registered ship. */
  Collection<Ship> all();
}