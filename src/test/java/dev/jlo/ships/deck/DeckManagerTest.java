package dev.jlo.ships.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavior tests for deck support deployment and cleanup. */
class DeckManagerTest {
  /** In-memory world recording barrier placement and clearing. */
  private static final class FakeWorld implements DeckSurface {
    final Set<String> barriers = new HashSet<>();

    final Set<String> blocked = new HashSet<>();

    final List<String> placements = new ArrayList<>();

    final List<String> removals = new ArrayList<>();

    final Set<String> alwaysBlocked = new HashSet<>();

    @Override
    public boolean canPlace(int x, int y, int z) {
      return !blocked.contains(key(x, y, z)) && !alwaysBlocked.contains(key(x, y, z));
    }

    @Override
    public boolean isClear(int x, int y, int z) {
      return !blocked.contains(key(x, y, z));
    }

    @Override
    public boolean placeBarrier(int x, int y, int z) {
      placements.add(key(x, y, z));
      if (blocked.contains(key(x, y, z)) || alwaysBlocked.contains(key(x, y, z))) {
        return false;
      }
      barriers.add(key(x, y, z));
      return true;
    }

    @Override
    public void removeBarrier(int x, int y, int z) {
      removals.add(key(x, y, z));
      barriers.remove(key(x, y, z));
    }

    private static String key(int x, int y, int z) {
      return x + "," + y + "," + z;
    }
  }

  @Test
  void deploysBarrierAtEveryExposedTop() {
    FakeWorld world = new FakeWorld();
    Ship ship = DeckSurfaceTestHelper.shipWith(new BlockPos(0, 0, 0));
    DeckManager manager = new DeckManager(world);
    boolean ok = manager.deploy(ship);
    assertTrue(ok);
    assertEquals(Set.of("100,201,300"), world.barriers);
  }

  @Test
  void failsWhenSupportCellIsBlocked() {
    FakeWorld world = new FakeWorld();
    world.alwaysBlocked.add("100,201,300");
    Ship ship = DeckSurfaceTestHelper.shipWith(new BlockPos(0, 0, 0));
    DeckManager manager = new DeckManager(world);
    boolean ok = manager.deploy(ship);
    assertFalse(ok);
    assertTrue(world.barriers.isEmpty());
  }

  @Test
  void removesAllDeployedBarriers() {
    FakeWorld world = new FakeWorld();
    Ship ship =
        DeckSurfaceTestHelper.shipWith(
            new BlockPos(0, 0, 0), new BlockPos(1, 1, 0), new BlockPos(2, 2, 0));
    DeckManager manager = new DeckManager(world);
    manager.deploy(ship);
    manager.remove(ship);
    assertTrue(world.barriers.isEmpty());
    assertEquals(world.placements, world.removals);
  }
}
