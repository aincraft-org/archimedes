package dev.jlo.ships.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for the ship assembly service. */
class ShipServiceImplTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");

  /** In-memory store, world mutator, and renderer for the service. */
  private static final class Fakes implements WorldMutator {
    final Map<UUID, Ship> persisted = new HashMap<>();
    final List<Ship> rendered = new ArrayList<>();
    final List<Ship> removedRuntime = new ArrayList<>();
    final Map<String, String> blocks = new HashMap<>();
    boolean restoreValid = true;

    ShipOrigin origin = new ShipOrigin(WORLD, 100, 200, 300);

    @Override
    public String blockDataAt(int x, int y, int z) {
      return blocks.getOrDefault(x + "," + y + "," + z, "minecraft:stone");
    }

    @Override
    public boolean clearBlocks(Ship ship) {
      for (var block : ship.blocks()) {
        blocks.remove((ship.origin().x() + block.pos().x()) + ","
            + (ship.origin().y() + block.pos().y()) + ","
            + (ship.origin().z() + block.pos().z()));
      }
      return true;
    }

    @Override
    public boolean validateRestore(Ship ship) {
      return restoreValid;
    }

    @Override
    public boolean restoreBlocks(Ship ship) {
      for (var block : ship.blocks()) {
        blocks.put((ship.origin().x() + block.pos().x()) + ","
            + (ship.origin().y() + block.pos().y()) + ","
            + (ship.origin().z() + block.pos().z()), block.blockData());
      }
      return true;
    }

    @Override
    public String lastError() {
      return "world mutation failed";
    }
  }

  @Test
  void assemblesAndPersistsShip() {
    Fakes fakes = new Fakes();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            WORLD);
    Ship result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNotNull(result);
    assertEquals(1, fakes.persisted.size());
    assertEquals(1, fakes.rendered.size());
  }

  @Test
  void findsOwnedShipInWorld() {
    Fakes fakes = new Fakes();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            WORLD);
    service.loadAll();
    Ship found = service.findOwnedInWorld(OWNER, WORLD);
    assertEquals(ship.id(), found.id());
  }

  @Test
  void disassembleRemovesRuntimeAndPersisted() {
    Fakes fakes = new Fakes();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            WORLD);
    service.loadAll();
    boolean ok = service.disassemble(ship.id(), OWNER, false);
    assertTrue(ok);
    assertTrue(fakes.persisted.isEmpty());
    assertEquals(1, fakes.removedRuntime.size());
  }

  @Test
  void disassembleRejectsNonOwnerWithoutOperator() {
    Fakes fakes = new Fakes();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            WORLD);
    service.loadAll();
    boolean ok = service.disassemble(ship.id(), UUID.randomUUID(), false);
    assertFalse(ok);
    assertEquals(1, fakes.persisted.size());
  }

  @Test
  void assembleFailsWhenScannerRejects() {
    Fakes fakes = new Fakes();
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> null,
            new RecordingRenderer(fakes),
            fakes,
            WORLD);
    Ship result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNull(result);
    assertEquals(0, fakes.persisted.size());
    assertEquals(0, fakes.rendered.size());
  }

  private record MemoryStore(Fakes fakes) implements ShipStoreLike {
    @Override
    public Map<UUID, Ship> loadAll() {
      return fakes.persisted;
    }

    @Override
    public void saveAll(Map<UUID, Ship> ships) {
      fakes.persisted.clear();
      fakes.persisted.putAll(ships);
    }
  }

  private record RecordingRenderer(Fakes fakes) implements ShipRendererLike {
    @Override
    public void render(Ship ship, ShipHolder holder) {
      fakes.rendered.add(ship);
      holder.accept(ship);
    }

    @Override
    public void removeRuntime(Ship ship) {
      fakes.removedRuntime.add(ship);
    }
  }
}