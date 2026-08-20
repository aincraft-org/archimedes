package dev.mintychochip.archimedes.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A/B comparison of today's full exposed-cell spawn against the streamed neighborhood on one hull.
 */
class CollisionAbFixtureTest {
  private static final UUID WORLD = UUID.randomUUID();

  @Test
  void streamedLiveCountIsAStrictSubsetOfFullSpawn() {
    Vehicle ship = boxHull();
    ExposedCellIndex index = ExposedCellIndex.build(ship);
    List<BlockPos> exposed = CollisionHull.exposedBlocks(ship);
    int aLive = exposed.size();
    CollisionBox observer = new CollisionBox(9.2, 5.0, 3.2, 9.8, 6.8, 3.8);
    List<BlockPos> bCells = index.cellsWithin(observer, 0, 0, 0, ExposedCellIndex.ENTER_RANGE);
    assertTrue(aLive > 0);
    assertTrue(bCells.size() < aLive, "B=" + bCells.size() + " A=" + aLive);
    assertTrue(exposed.containsAll(bCells));
    CollisionVolumePool pool = new CollisionVolumePool();
    UUID player = UUID.randomUUID();
    pool.reconcile(Map.of(player, Set.copyOf(bCells)), Set.of(player));
    assertEquals(bCells.size(), pool.live().size());
    pool.reconcile(Map.of(), Set.of());
    assertEquals(0, pool.live().size());
  }

  private static Vehicle boxHull() {
    List<BlockPos> blocks = new ArrayList<>();
    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 5; y++) {
        for (int z = 0; z < 8; z++) {
          boolean shell = x == 0 || x == 19 || y == 0 || y == 4 || z == 0 || z == 7;
          if (shell) {
            blocks.add(new BlockPos(x, y, z));
          }
        }
      }
    }
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(WORLD, 0, 0, 0),
        blocks.stream().map(position -> new ShipBlock(position, "minecraft:stone")).toList());
  }
}
