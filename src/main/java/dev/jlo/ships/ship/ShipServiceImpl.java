package dev.jlo.ships.ship;

import dev.jlo.ships.deck.DeckManager;
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

/**
 * Default ship service: scans a connected component, snapshots each block's exact data, removes
 * source blocks, persists the model, then renders it. Disassembly validates destination occupancy,
 * restores blocks, removes runtime entities and supports, and only then persists removal. Any
 * failure rolls back.
 */
public final class ShipServiceImpl implements ShipService {
  /** Persistence backend. */
  private final ShipStoreLike store;

  /** Component scanner. */
  private final ComponentScanner scanner;

  /** Runtime renderer. */
  private final ShipRendererLike renderer;

  /** World mutator. */
  private final WorldMutator mutator;

  /** Deck support manager. */
  private final DeckManager deck;

  /** Buoyancy engine. */
  private final dev.jlo.ships.buoyancy.Buoyancy buoyancy;

  /** World this service is bound to. */
  private final UUID worldId;

  /** Registered ships keyed by identifier. */
  private final Map<UUID, Ship> ships = new LinkedHashMap<>();

  /** Last operation failure message. */
  private String lastError;

  /**
   * Creates the service.
   *
   * @param store the persistence backend
   * @param scanner the component scanner
   * @param renderer the runtime renderer
   * @param mutator the world mutator
   * @param deck the deck support manager
   * @param buoyancy the buoyancy engine
   * @param worldId the bound world identifier
   */
  public ShipServiceImpl(
      ShipStoreLike store,
      ComponentScanner scanner,
      ShipRendererLike renderer,
      WorldMutator mutator,
      DeckManager deck,
      dev.jlo.ships.buoyancy.Buoyancy buoyancy,
      UUID worldId) {
    this.store = store;
    this.scanner = scanner;
    this.renderer = renderer;
    this.mutator = mutator;
    this.deck = deck;
    this.buoyancy = buoyancy;
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
    // Remove source blocks first; if that fails, nothing is persisted or rendered.
    if (!mutator.clearBlocks(ship)) {
      lastError = mutator.lastError();
      return null;
    }
    // Deploy walkable deck supports before rendering so a blocked cell
    // aborts the whole assembly with the world still restorable.
    if (!deck.deploy(ship)) {
      mutator.restoreBlocks(ship);
      lastError = "Deck supports are obstructed: " + deck.lastError();
      return null;
    }
    try {
      renderer.render(ship, this::storeAndRegister);
    } catch (IllegalStateException failure) {
      // Render failed after mutation: restore exact snapshots, clear any
      // partial runtime entities and supports, and persist the removal so a
      // restart cannot resurrect a half-assembled ship.
      rollback(ship, failure.getMessage());
      return null;
    } catch (IllegalArgumentException failure) {
      rollback(ship, failure.getMessage());
      return null;
    }
    if (!buoyancy.rise(ship)) {
      rollback(ship, "Buoyancy path blocked");
      return null;
    }
    return ships.get(ship.id());
  }

  private void rollback(Ship ship, String message) {
    deck.remove(ship);
    mutator.restoreBlocks(ship);
    renderer.removeRuntime(ship);
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
    // Validate every destination before mutating anything.
    if (!mutator.validateRestore(ship)) {
      lastError = mutator.lastError();
      return false;
    }
    if (!mutator.restoreBlocks(ship)) {
      lastError = mutator.lastError();
      renderer.render(ship, ignored -> {});
      return false;
    }
    deck.remove(ship);
    renderer.removeRuntime(ship);
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
    return ships;
  }

  @Override
  public void saveAll() {
    persistAll();
  }

  @Override
  public void removeAllRuntime() {
    for (Ship ship : ships.values()) {
      renderer.removeRuntime(ship);
      deck.remove(ship);
      buoyancy.clear(ship);
    }
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
  public boolean toggleBuoyancy(UUID requesterId, UUID worldId) {
    Ship ship = findOwnedInWorld(requesterId, worldId);
    if (ship == null) {
      lastError = "No ship in this world";
      return false;
    }
    ship.setBuoyancyEnabled(!ship.buoyancyEnabled());
    persistAll();
    return true;
  }

  @Override
  public boolean sink(UUID requesterId, UUID worldId, int blocks) {
    Ship ship = findOwnedInWorld(requesterId, worldId);
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

  private void storeAndRegister(Ship ship) {
    ships.put(ship.id(), ship);
    persistAll();
  }

  private void persistAll() {
    store.saveAll(ships);
  }
}
