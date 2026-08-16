package dev.jlo.archimedes.ship;

import dev.jlo.archimedes.model.Ship;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the ship registry and runtime lifecycle for assembly, lookup, and disassembly. Backed by a
 * persistence store and renderer.
 */
public interface ShipService {
  /**
   * Assembles a ship at the given seed, returning it or null on failure.
   *
   * @param playerId the assembling player
   * @param x the seed x coordinate
   * @param y the seed y coordinate
   * @param z the seed z coordinate
   * @param worldId the world identifier
   * @return the assembled ship, or null on failure
   */
  Ship assembleAt(UUID playerId, int x, int y, int z, UUID worldId);

  /**
   * Returns the ship owned by a player in their current world, or null.
   *
   * @param playerId the owning player
   * @param worldId the world identifier
   * @return the owned ship, or null
   */
  Ship findOwnedInWorld(UUID playerId, UUID worldId);

  /**
   * Disassembles a ship.
   *
   * @param shipId the ship identifier
   * @param requesterId the requesting player
   * @param operator whether the requester is an operator
   * @return true on success
   */
  boolean disassemble(UUID shipId, UUID requesterId, boolean operator);

  /**
   * @return the last operation failure message
   */
  String lastError();

  /**
   * Loads all persisted ships into the registry.
   *
   * @return all registered ships keyed by identifier
   */
  Map<UUID, Ship> loadAll();

  /** Persists all registered ships. */
  void saveAll();

  /** Removes all runtime entities for registered ships. */
  void removeAllRuntime();

  /**
   * @return every registered ship
   */
  Collection<Ship> all();

  /** Integrates one buoyancy tick for every registered ship. */
  void tick();

  /**
   * Toggles buoyancy for the requester's owned ship in the world.
   *
   * @param requesterId the requesting player
   * @param worldId the world identifier
   * @return true on success
   */
  boolean toggleBuoyancy(UUID requesterId, UUID worldId);

  /**
   * Lowers the requester's owned ship in the world.
   *
   * @param requesterId the requesting player
   * @param worldId the world identifier
   * @param blocks the number of blocks to lower
   * @return true on success
   */
  boolean sink(UUID requesterId, UUID worldId, int blocks);
}
