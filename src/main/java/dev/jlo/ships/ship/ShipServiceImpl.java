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

/**
 * Default ship service: scans a connected component, snapshots each block's
 * exact data, removes source blocks, persists the model, then renders it.
 * Disassembly validates destination occupancy, restores blocks, removes
 * runtime entities, and only then persists removal. Any failure rolls back.
 */
public final class ShipServiceImpl implements ShipService {
  private final ShipStoreLike store;
  private final ComponentScanner scanner;
  private final ShipRendererLike renderer;
  private final WorldMutator mutator;
  private final UUID worldId;
  private final Map<UUID, Ship> ships = new LinkedHashMap<>();
  private String lastError;

  /** Creates the service. */
  public ShipServiceImpl(
      ShipStoreLike store,
      ComponentScanner scanner,
      ShipRendererLike renderer,
      WorldMutator mutator,
      UUID worldId) {
    this.store = store;
    this.scanner = scanner;
    this.renderer = renderer;
    this.mutator = mutator;
    this.worldId = worldId;
  }

  @Override
  public Ship assembleAt(UUID playerId, int x, int y, int z, UUID worldId) {
    if (!worldId.equals(this.worldId)) {
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
        new Ship(UUID.randomUUID(), playerId, new ShipOrigin(worldId, x, y, z), blocks);
    // Remove source blocks first; if that fails, nothing is persisted or rendered.
    if (!mutator.clearBlocks(ship)) {
      lastError = mutator.lastError();
      return null;
    }
    try {
      renderer.render(ship, this::storeAndRegister);
    } catch (RuntimeException failure) {
      // Render failed after mutation: restore exact snapshots, clear any
      // partial runtime entities, and persist the removal so a restart
      // cannot resurrect a half-assembled ship.
      mutator.restoreBlocks(ship);
      renderer.removeRuntime(ship);
      ships.remove(ship.id());
      persistAll();
      lastError = "Assembly failed: " + failure.getMessage();
      return null;
    }
    return ships.get(ship.id());
  }

  @Override
  public Ship findOwnedInWorld(UUID playerId, UUID worldId) {
    for (Ship ship : ships.values()) {
      if (ship.ownerId().equals(playerId) && ship.origin().worldId().equals(worldId)) {
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
    renderer.removeRuntime(ship);
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
    }
  }

  @Override
  public Collection<Ship> all() {
    return List.copyOf(ships.values());
  }

  private void storeAndRegister(Ship ship) {
    ships.put(ship.id(), ship);
    persistAll();
  }

  private void persistAll() {
    store.saveAll(ships);
  }
}