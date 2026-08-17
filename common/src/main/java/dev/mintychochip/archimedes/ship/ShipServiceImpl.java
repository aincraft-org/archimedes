package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.sail.SailShipTemplate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Default ship service for persistence, world mutation, and runtime lifecycle. */
public final class ShipServiceImpl implements ShipService {
  /** Persistence backend. */
  private final ShipStoreLike store;

  /** Component scanner. */
  private final ComponentScanner scanner;

  /** Runtime composition. */
  private final ShipRuntime runtime;

  /** World block mutator. */
  private final WorldMutator mutator;

  /** Ship physics controller. */
  private final dev.mintychochip.archimedes.phys.ShipPhysics shipPhysics;

  /** Whether buoyancy is enabled globally. */
  private final boolean buoyancyEnabled;

  /** Whether assembly is enabled in the bound world. */
  private final boolean worldEnabled;

  /** World in which this service operates. */
  private final UUID worldId;

  /** Loaded ships keyed by identifier. */
  private final Map<UUID, Ship> ships = new LinkedHashMap<>();

  /** Most recent user-facing failure message. */
  private String lastError;

  /**
   * Returns the runtime composition for plugin lifecycle cleanup.
   *
   * @return runtime composition
   */
  public ShipRuntime runtime() {
    return runtime;
  }

  /**
   * Creates a ship service.
   *
   * @param store persistence backend
   * @param scanner component scanner
   * @param runtime runtime composition
   * @param mutator world block mutator
   * @param shipPhysics ship physics controller
   * @param buoyancyEnabled whether buoyancy is enabled globally
   * @param worldEnabled whether assembly is enabled in the bound world
   * @param worldId operating world identifier
   */
  public ShipServiceImpl(
      ShipStoreLike store,
      ComponentScanner scanner,
      ShipRuntime runtime,
      WorldMutator mutator,
      dev.mintychochip.archimedes.phys.ShipPhysics shipPhysics,
      boolean buoyancyEnabled,
      boolean worldEnabled,
      UUID worldId) {
    this.store = store;
    this.scanner = scanner;
    this.runtime = runtime;
    this.mutator = mutator;
    this.shipPhysics = shipPhysics;
    this.buoyancyEnabled = buoyancyEnabled;
    this.worldEnabled = worldEnabled;
    this.worldId = worldId;
  }

  /**
   * Assembles a scanned component at a world coordinate.
   *
   * @param playerId requesting owner
   * @param x component origin x
   * @param y component origin y
   * @param z component origin z
   * @param targetWorldId requested world
   * @return the created ship, or {@code null} when validation or startup fails
   */
  @Override
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public Ship assembleAt(UUID playerId, int x, int y, int z, UUID targetWorldId) {
    if (!targetWorldId.equals(worldId)) {
      lastError = "Ship assembly is not permitted in this world";
      return null;
    }
    if (!worldEnabled) {
      lastError = "Ship assembly is disabled in this world";
      return null;
    }
    List<BlockPos> component = scanner.scan(x, y, z);
    if (component == null) {
      lastError = "Component exceeds the allowed block limit or contains a forbidden material";
      return null;
    }
    List<ShipBlock> blocks = new ArrayList<>(component.size());
    for (BlockPos pos : component) {
      blocks.add(new ShipBlock(pos, mutator.blockDataAt(x + pos.x(), y + pos.y(), z + pos.z())));
    }
    Ship ship =
        new Ship(UUID.randomUUID(), playerId, new ShipOrigin(targetWorldId, x, y, z), blocks);
    if (!mutator.clearBlocks(ship)) {
      lastError = mutator.lastError();
      return null;
    }
    boolean runtimeStarted = false;
    boolean buoyancyStarted = false;
    try {
      runtimeStarted = true;
      runtime.spawn(ship);
      ships.put(ship.id(), ship);
      if (buoyancyEnabled) {
        buoyancyStarted = true;
        if (!shipPhysics.rise(ship)) {
          throw new ShipRuntimeException(new IllegalStateException("Buoyancy path blocked"));
        }
      }
      persistAll();
      return ships.get(ship.id());
    } catch (RuntimeException failure) {
      ShipRuntimeException normalized =
          failure instanceof ShipRuntimeException
              ? (ShipRuntimeException) failure
              : new ShipRuntimeException(failure);
      rollback(ship, normalized, runtimeStarted, buoyancyStarted);
      return null;
    }
  }

  /**
   * Spawns the predetermined sail template at an origin. World blocks are not scanned or cleared;
   * disassembly later restores the template into empty space.
   *
   * @param playerId requesting owner
   * @param targetWorldId requested world
   * @param x origin x
   * @param y origin y
   * @param z origin z
   * @return the created ship, or {@code null} when validation or startup fails
   */
  @Override
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public Ship spawnSail(UUID playerId, UUID targetWorldId, int x, int y, int z) {
    if (!targetWorldId.equals(worldId)) {
      lastError = "Ship assembly is not permitted in this world";
      return null;
    }
    if (!worldEnabled) {
      lastError = "Ship assembly is disabled in this world";
      return null;
    }
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            playerId,
            new ShipOrigin(targetWorldId, x, y, z),
            SailShipTemplate.blocks());
    boolean runtimeStarted = false;
    boolean buoyancyStarted = false;
    try {
      runtimeStarted = true;
      runtime.spawn(ship);
      ships.put(ship.id(), ship);
      if (buoyancyEnabled) {
        buoyancyStarted = true;
        if (!shipPhysics.rise(ship)) {
          throw new ShipRuntimeException(new IllegalStateException("Buoyancy path blocked"));
        }
      }
      persistAll();
      return ships.get(ship.id());
    } catch (RuntimeException failure) {
      ShipRuntimeException normalized =
          failure instanceof ShipRuntimeException
              ? (ShipRuntimeException) failure
              : new ShipRuntimeException(failure);
      rollbackSpawn(ship, normalized, runtimeStarted, buoyancyStarted);
      return null;
    }
  }

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private void rollbackSpawn(
      Ship ship, ShipRuntimeException failure, boolean runtimeStarted, boolean buoyancyStarted) {
    if (runtimeStarted) {
      try {
        runtime.remove(ship);
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(normalizeCleanup(cleanup));
      }
    }
    if (buoyancyStarted) {
      try {
        shipPhysics.clear(ship);
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(normalizeCleanup(cleanup));
      }
    }
    ships.remove(ship.id());
    try {
      persistAll();
    } catch (RuntimeException cleanup) {
      failure.addSuppressed(normalizeCleanup(cleanup));
    }
    Throwable cause = failure.getCause();
    String reason = cause == null ? failure.getMessage() : cause.getMessage();
    lastError = reason == null ? "unknown failure" : reason;
  }

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private void rollback(
      Ship ship, ShipRuntimeException failure, boolean runtimeStarted, boolean buoyancyStarted) {
    boolean restored = true;
    try {
      if (!mutator.restoreBlocks(ship)) {
        restored = false;
        failure.addSuppressed(new IllegalStateException(mutator.lastError()));
      }
    } catch (RuntimeException cleanup) {
      restored = false;
      failure.addSuppressed(normalizeCleanup(cleanup));
    }
    if (runtimeStarted) {
      try {
        runtime.remove(ship);
      } catch (RuntimeException cleanup) {
        restored = false;
        failure.addSuppressed(normalizeCleanup(cleanup));
      }
    }
    if (buoyancyStarted) {
      try {
        shipPhysics.clear(ship);
      } catch (RuntimeException cleanup) {
        restored = false;
        failure.addSuppressed(normalizeCleanup(cleanup));
      }
    }
    ships.remove(ship.id());
    try {
      persistAll();
    } catch (RuntimeException cleanup) {
      restored = false;
      failure.addSuppressed(normalizeCleanup(cleanup));
    }
    Throwable cause = failure.getCause();
    String reason = cause == null ? failure.getMessage() : cause.getMessage();
    lastError = reason == null ? "unknown failure" : reason;
    if (!restored) {
      throw failure;
    }
  }

  private static ShipRuntimeException normalizeCleanup(RuntimeException cleanup) {
    return cleanup instanceof ShipRuntimeException
        ? (ShipRuntimeException) cleanup
        : new ShipRuntimeException(cleanup);
  }

  /**
   * @return the first loaded ship owned by the player in the requested world, or {@code null}
   */
  @Override
  public Ship findOwnedInWorld(UUID playerId, UUID targetWorldId) {
    for (Ship ship : ships.values()) {
      if (ship.ownerId().equals(playerId) && ship.origin().worldId().equals(targetWorldId)) {
        return ship;
      }
    }
    return null;
  }

  /**
   * Restores and removes a ship after checking ownership and world block safety.
   *
   * @return whether disassembly completed; {@link #lastError()} describes a rejection
   */
  @Override
  public boolean disassemble(UUID shipId, UUID requesterId, boolean operator) {
    Ship ship = ships.get(shipId);
    if (ship == null) {
      lastError = "Ship not found";
      return false;
    }
    if (!operator && !ship.ownerId().equals(requesterId)) {
      lastError = "You do not own this ship";
      return false;
    }
    if (!mutator.validateRestore(ship)) {
      lastError = mutator.lastError();
      return false;
    }
    if (!mutator.restoreBlocks(ship)) {
      lastError = mutator.lastError();
      runtime.spawn(ship);
      return false;
    }
    runtime.remove(ship);
    shipPhysics.clear(ship);
    ships.remove(shipId);
    persistAll();
    return true;
  }

  /**
   * @return the most recent user-facing failure message, or {@code null}
   */
  @Override
  public String lastError() {
    return lastError;
  }

  /**
   * Loads persisted ships, clears stale runtime tags, and spawns the loaded set.
   *
   * @return the currently loaded ships keyed by identifier
   * @throws IllegalStateException if loading or runtime startup fails
   */
  @Override
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public Map<UUID, Ship> loadAll() {
    List<Ship> spawned = new ArrayList<>();
    Ship current = null;
    String phase = "store-load";
    RuntimeException primary;
    try {
      Map<UUID, Ship> loaded = new LinkedHashMap<>(store.loadAll());
      ships.clear();
      phase = "initial-tag-sweep";
      runtime.removeAllTagged();
      phase = "spawn";
      for (Ship ship : loaded.values()) {
        current = ship;
        runtime.spawn(ship);
        spawned.add(ship);
        ships.put(ship.id(), ship);
      }
      return ships;
    } catch (RuntimeException failure) {
      primary = failure;
    }
    for (Ship ship : spawned) {
      try {
        runtime.remove(ship);
      } catch (RuntimeException cleanup) {
        primary.addSuppressed(normalizeCleanup(cleanup));
      }
    }
    try {
      runtime.removeAllTagged();
    } catch (RuntimeException cleanup) {
      primary.addSuppressed(normalizeCleanup(cleanup));
    }
    ships.clear();
    String shipId = current == null ? "unknown" : current.id().toString();
    throw new IllegalStateException("Failed during " + phase + " for ship " + shipId, primary);
  }

  /** Persists all currently loaded ships. */
  @Override
  public void saveAll() {
    persistAll();
  }

  /** Removes all loaded ships from the runtime without deleting persisted data. */
  @Override
  public void removeAllRuntime() {
    runtime.removeAll(ships.values());
  }

  /**
   * @return an immutable snapshot of currently loaded ships
   */
  @Override
  public Collection<Ship> all() {
    return List.copyOf(ships.values());
  }

  /** Advances physics for every loaded ship and persists when any ship moved. */
  @Override
  public void tick() {
    boolean moved = false;
    for (Ship ship : ships.values()) {
      moved |= shipPhysics.tick(ship);
    }
    if (moved) {
      persistAll();
    }
  }

  /**
   * Toggles buoyancy for the requester's ship in the target world.
   *
   * @return whether a matching ship was found and persisted
   */
  @Override
  public boolean toggleBuoyancy(UUID requesterId, UUID targetWorldId) {
    Ship ship = findOwnedInWorld(requesterId, targetWorldId);
    if (ship == null) {
      lastError = "No ship in this world";
      return false;
    }
    ship.setBuoyancyEnabled(!ship.buoyancyEnabled());
    persistAll();
    return true;
  }

  /**
   * Attempts to sink the requester's ship by a positive block count.
   *
   * @return whether movement and persistence succeeded
   */
  @Override
  public boolean sink(UUID requesterId, UUID targetWorldId, int blocks) {
    if (blocks <= 0) {
      lastError = "Block count must be positive";
      return false;
    }
    Ship ship = findOwnedInWorld(requesterId, targetWorldId);
    if (ship == null) {
      lastError = "No ship in this world";
      return false;
    }
    if (!shipPhysics.sink(ship, blocks)) {
      lastError = "path blocked";
      return false;
    }
    persistAll();
    return true;
  }

  private void persistAll() {
    store.saveAll(ships);
  }
}
