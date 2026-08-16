package dev.jlo.ships.ship;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
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

  /** Buoyancy controller. */
  private final dev.jlo.ships.buoyancy.Buoyancy buoyancy;

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
   * @param buoyancy buoyancy controller
   * @param buoyancyEnabled whether buoyancy is enabled globally
   * @param worldEnabled whether assembly is enabled in the bound world
   * @param worldId operating world identifier
   */
  public ShipServiceImpl(
      ShipStoreLike store,
      ComponentScanner scanner,
      ShipRuntime runtime,
      WorldMutator mutator,
      dev.jlo.ships.buoyancy.Buoyancy buoyancy,
      boolean buoyancyEnabled,
      boolean worldEnabled,
      UUID worldId) {
    this.store = store;
    this.scanner = scanner;
    this.runtime = runtime;
    this.mutator = mutator;
    this.buoyancy = buoyancy;
    this.buoyancyEnabled = buoyancyEnabled;
    this.worldEnabled = worldEnabled;
    this.worldId = worldId;
  }

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
        if (!buoyancy.rise(ship)) {
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

  private void rollback(
      Ship ship, ShipRuntimeException failure, boolean runtimeStarted, boolean buoyancyStarted) {
    boolean restored = true;
    try {
      if (!mutator.restoreBlocks(ship)) {
        restored = false;
        failure.addSuppressed(new IllegalStateException(mutator.lastError()));
      }
    } catch (ShipRuntimeException cleanup) {
      restored = false;
      failure.addSuppressed(cleanup);
    }
    if (runtimeStarted) {
      try {
        runtime.remove(ship);
      } catch (ShipRuntimeException cleanup) {
        restored = false;
        failure.addSuppressed(cleanup);
      }
    }
    if (buoyancyStarted) {
      try {
        buoyancy.clear(ship);
      } catch (ShipRuntimeException cleanup) {
        restored = false;
        failure.addSuppressed(cleanup);
      }
    }
    ships.remove(ship.id());
    try {
      persistAll();
    } catch (ShipRuntimeException cleanup) {
      restored = false;
      failure.addSuppressed(cleanup);
    }
    Throwable cause = failure.getCause();
    String reason = cause == null ? failure.getMessage() : cause.getMessage();
    lastError = "Assembly failed: " + (reason == null ? "unknown failure" : reason);
    if (!restored) {
      throw failure;
    }
  }

  @Override
  public Ship findOwnedInWorld(UUID playerId, UUID targetWorldId) {
    for (Ship ship : ships.values()) {
      if (ship.ownerId().equals(playerId) && ship.origin().worldId().equals(targetWorldId)) {
        return ship;
      }
    }
    return null;
  }

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
    buoyancy.clear(ship);
    ships.remove(shipId);
    persistAll();
    return true;
  }

  @Override
  public String lastError() {
    return lastError;
  }

  @Override
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
    } catch (ShipRuntimeException failure) {
      primary = failure;
    }
    for (Ship ship : spawned) {
      try {
        runtime.remove(ship);
      } catch (ShipRuntimeException cleanup) {
        primary.addSuppressed(cleanup);
      }
    }
    try {
      runtime.removeAllTagged();
    } catch (ShipRuntimeException cleanup) {
      primary.addSuppressed(cleanup);
    }
    ships.clear();
    String shipId = current == null ? "unknown" : current.id().toString();
    throw new IllegalStateException("Failed during " + phase + " for ship " + shipId, primary);
  }

  @Override
  public void saveAll() {
    persistAll();
  }

  @Override
  public void removeAllRuntime() {
    runtime.removeAll(ships.values());
  }

  @Override
  public Collection<Ship> all() {
    return List.copyOf(ships.values());
  }

  @Override
  public void tick() {
    boolean moved = false;
    for (Ship ship : ships.values()) {
      moved |= buoyancy.tick(ship);
    }
    if (moved) {
      persistAll();
    }
  }

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

  @Override
  public boolean sink(UUID requesterId, UUID targetWorldId, int blocks) {
    Ship ship = findOwnedInWorld(requesterId, targetWorldId);
    if (ship == null) {
      lastError = "No ship in this world";
      return false;
    }
    if (!buoyancy.sink(ship, blocks)) {
      lastError = "Cannot lower ship: path blocked";
      return false;
    }
    persistAll();
    return true;
  }

  private void persistAll() {
    store.saveAll(ships);
  }
}
