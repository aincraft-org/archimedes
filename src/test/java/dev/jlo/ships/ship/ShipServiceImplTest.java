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
import dev.jlo.ships.model.ShipPose;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for the ship assembly service. */
class ShipServiceImplTest {
  /** Common capturable material. */
  private static final String STONE = "minecraft:stone";

  /** Single-block origin key. */
  private static final String ORIGIN_KEY = "100,200,300";

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
      return blocks.getOrDefault(x + "," + y + "," + z, STONE);
    }

    @Override
    public boolean clearBlocks(Ship ship) {
      for (var block : ship.blocks()) {
        blocks.remove(
            (ship.origin().x() + block.pos().x())
                + ","
                + (baseY(ship) + block.pos().y())
                + ","
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
        blocks.put(
            (ship.origin().x() + block.pos().x())
                + ","
                + (baseY(ship) + block.pos().y())
                + ","
                + (ship.origin().z() + block.pos().z()),
            block.blockData());
      }
      return true;
    }

    @Override
    public String lastError() {
      return "world mutation failed";
    }

    private static int baseY(Ship ship) {
      return ship.origin().y() + ship.pose().anchorDy();
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
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            new NoopDeck(),
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
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            new NoopDeck(),
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
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            new NoopDeck(),
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
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            new NoopDeck(),
            WORLD);
    service.loadAll();
    boolean ok = service.disassemble(ship.id(), UUID.randomUUID(), false);
    assertFalse(ok);
    assertEquals(1, fakes.persisted.size());
  }

  @Test
  void disassembleRestoresAtPoseAnchor() {
    Fakes fakes = new Fakes();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)),
            new ShipPose(3.0),
            true);
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new RecordingRenderer(fakes),
            fakes,
            new NoopDeck(),
            WORLD);
    service.loadAll();
    boolean ok = service.disassemble(ship.id(), OWNER, false);
    assertTrue(ok);
    // restored at origin.y(200) + anchor(3) = 203
    assertEquals(STONE, fakes.blocks.get("100,203,300"));
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
            new NoopDeck(),
            WORLD);
    Ship result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNull(result);
    assertEquals(0, fakes.persisted.size());
    assertEquals(0, fakes.rendered.size());
  }

  @Test
  void assembleRollsBackWhenRenderFails() {
    Fakes fakes = new Fakes();
    fakes.blocks.put(ORIGIN_KEY, STONE);
    ShipRendererLike throwing =
        new ShipRendererLike() {
          @Override
          public void render(Ship s, ShipHolder holder) {
            throw new IllegalStateException("no display slots");
          }

          @Override
          public void removeRuntime(Ship s) {
            fakes.removedRuntime.add(s);
          }

          @Override
          public void reposition(Ship s, double oldY, double newY) {}
        };
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            throwing,
            fakes,
            new NoopDeck(),
            WORLD);
    Ship result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNull(result);
    assertEquals(0, fakes.persisted.size());
    assertEquals(STONE, fakes.blocks.get(ORIGIN_KEY));
  }

  @Test
  void assemblePersistsRollbackWhenHolderAlreadySaved() {
    Fakes fakes = new Fakes();
    fakes.blocks.put(ORIGIN_KEY, STONE);
    ShipRendererLike holderThenThrow =
        new ShipRendererLike() {
          @Override
          public void render(Ship s, ShipHolder holder) {
            holder.accept(s);
            throw new IllegalStateException("second phase failed");
          }

          @Override
          public void removeRuntime(Ship s) {
            fakes.removedRuntime.add(s);
          }

          @Override
          public void reposition(Ship s, double oldY, double newY) {}
        };
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            holderThenThrow,
            fakes,
            new NoopDeck(),
            WORLD);
    Ship result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNull(result);
    // The store must not retain the half-saved ship after rollback.
    assertEquals(0, fakes.persisted.size());
    assertEquals(STONE, fakes.blocks.get(ORIGIN_KEY));
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

    @Override
    public void reposition(Ship ship, double oldY, double newY) {}
  }
}

/** A deck manager that never blocks. */
final class NoopDeck extends dev.jlo.ships.deck.DeckManager {
  NoopDeck() {
    super(
        new dev.jlo.ships.deck.DeckSurface() {
          @Override
          public boolean canPlace(int x, int y, int z) {
            return true;
          }

          @Override
          public boolean isClear(int x, int y, int z) {
            return true;
          }

          @Override
          public boolean placeBarrier(int x, int y, int z) {
            return true;
          }

          @Override
          public void removeBarrier(int x, int y, int z) {}
        });
  }
}
