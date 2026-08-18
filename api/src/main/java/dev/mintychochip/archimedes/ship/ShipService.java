package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Ship;
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
   * Spawns a predetermined sail ship at the given origin without scanning or clearing world blocks.
   *
   * @param playerId the owning player
   * @param worldId the world identifier
   * @param x origin x
   * @param y origin y
   * @param z origin z
   * @return the spawned ship, or null on failure
   */
  default Ship spawnSail(UUID playerId, UUID worldId, int x, int y, int z) {
    return spawnSail(playerId, worldId, x, y, z, "medium");
  }

  /**
   * Spawns a predetermined sail ship of the given size ({@code small}, {@code medium}, {@code
   * large}).
   *
   * @param playerId the owning player
   * @param worldId the world identifier
   * @param x origin x
   * @param y origin y
   * @param z origin z
   * @param size named hull size
   * @return the spawned ship, or null on failure
   */
  Ship spawnSail(UUID playerId, UUID worldId, int x, int y, int z, String size);

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
