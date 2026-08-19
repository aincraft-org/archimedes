package dev.mintychochip.archimedes.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests deterministic exposed collision hull selection. */
class CollisionHullTest {
  private static final UUID WORLD = UUID.randomUUID();

  @Test
  void singleBlockIsExposed() {
    Vehicle ship = ship(List.of(new BlockPos(0, 0, 0)));
    assertEquals(List.of(new BlockPos(0, 0, 0)), CollisionHull.exposedBlocks(ship));
  }

  @Test
  void solidCubeExcludesNoBlocksBecauseSurfaceIsExposed() {
    List<BlockPos> blocks = new ArrayList<>();
    for (int x = -1; x <= 1; x++) {
      for (int y = -1; y <= 1; y++) {
        for (int z = -1; z <= 1; z++) {
          blocks.add(new BlockPos(x, y, z));
        }
      }
    }
    List<BlockPos> exposed = CollisionHull.exposedBlocks(ship(blocks));
    assertEquals(26, exposed.size());
    assertEquals(false, exposed.contains(new BlockPos(0, 0, 0)));
    assertEquals(new BlockPos(-1, -1, -1), exposed.get(0));
  }

  @Test
  void orderingIsLexicographicRegardlessOfInputOrder() {
    Vehicle ship =
        ship(
            List.of(
                new BlockPos(2, 0, 0),
                new BlockPos(-1, 3, 4),
                new BlockPos(-1, 2, 9),
                new BlockPos(0, 0, 0)));
    assertEquals(
        List.of(
            new BlockPos(-1, 2, 9),
            new BlockPos(-1, 3, 4),
            new BlockPos(0, 0, 0),
            new BlockPos(2, 0, 0)),
        CollisionHull.exposedBlocks(ship));
  }

  @Test
  void topExposedOnlyIncludesBlocksWithMissingUpperNeighbor() {
    Vehicle ship = ship(List.of(new BlockPos(0, 0, 0), new BlockPos(0, 1, 0)));
    assertEquals(List.of(new BlockPos(0, 1, 0)), CollisionHull.topExposedBlocks(ship));
  }

  private static Vehicle ship(List<BlockPos> positions) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(WORLD, 100, 200, 300),
        positions.stream().map(position -> new ShipBlock(position, "minecraft:stone")).toList());
  }
}
