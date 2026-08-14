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
  private final ShipStoreLike store;
  private final ComponentScanner scanner;
  private final ShipRuntime runtime;
  private final WorldMutator mutator;
  private final dev.jlo.ships.buoyancy.Buoyancy buoyancy;
  private final boolean buoyancyEnabled;
  private final UUID worldId;
  private final Map<UUID, Ship> ships = new LinkedHashMap<>();
  private String lastError;

  /** Compatibility constructor for legacy tests; deck operations are ignored. */
  public ShipServiceImpl(
      ShipStoreLike store,
      ComponentScanner scanner,
      ShipRendererLike renderer,
      WorldMutator mutator,
      dev.jlo.ships.deck.DeckManager ignoredDeck,
      dev.jlo.ships.buoyancy.Buoyancy buoyancy,
      boolean buoyancyEnabled,
      UUID worldId) {
    this(
        store,
        scanner,
        new LegacyRuntime(renderer),
        mutator,
        buoyancy,
        buoyancyEnabled,
        worldId);
  }

  public ShipServiceImpl(
      ShipStoreLike store,
      ComponentScanner scanner,
      ShipRuntime runtime,
      WorldMutator mutator,
      dev.jlo.ships.buoyancy.Buoyancy buoyancy,
      boolean buoyancyEnabled,
      UUID worldId) {
    this.store = store;
    this.scanner = scanner;
    this.runtime = runtime;
    this.mutator = mutator;
    this.buoyancy = buoyancy;
    this.buoyancyEnabled = buoyancyEnabled;
    this.worldId = worldId;
  }

  @Override
  public Ship assembleAt(UUID playerId, int x, int y, int z, UUID targetWorldId) {
    if (!targetWorldId.equals(worldId)) {
      lastError = "Ship assembly is not permitted in this world";
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
    try {
      runtime.spawn(ship);
      ships.put(ship.id(), ship);
      persistAll();
    } catch (RuntimeException failure) {
      rollback(ship, failure.getMessage());
      return null;
    }
    if (buoyancyEnabled && !buoyancy.rise(ship)) {
      rollback(ship, "Buoyancy path blocked");
      return null;
    }
    return ships.get(ship.id());
  }

  private void rollback(Ship ship, String message) {
    mutator.restoreBlocks(ship);
    runtime.remove(ship);
    buoyancy.clear(ship);
    ships.remove(ship.id());
    persistAll();
    lastError = "Assembly failed: " + message;
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
    ships.clear();
    ships.putAll(store.loadAll());
    for (Ship ship : ships.values()) {
      runtime.spawn(ship);
    }
    return ships;
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

  private static final class LegacyRuntime implements ShipRuntime {
    private final ShipRendererLike renderer;

    private LegacyRuntime(ShipRendererLike renderer) {
      this.renderer = renderer;
    }

    @Override
    public void spawn(Ship ship) {
      renderer.render(ship, ignored -> {});
    }

    @Override
    public void move(Ship ship, double oldY, double newY) {
      renderer.reposition(ship, oldY, newY);
    }

    @Override
    public void remove(Ship ship) {
      renderer.removeRuntime(ship);
    }

    @Override
    public void removeAll(Collection<Ship> ships) {
      for (Ship ship : ships) {
        remove(ship);
      }
    }
  }

  private void persistAll() {
    store.saveAll(ships);
  }
}
