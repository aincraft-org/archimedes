package dev.jlo.ships.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for walkable deck support resolution. */
class DeckSurfaceTest {
  @Test
  void exposesTopOfSingleBlock() {
    Ship ship = shipWith(new BlockPos(0, 0, 0));
    Set<long[]> supports = DeckSurface.supportPositions(ship);
    assertEquals(1, supports.size());
    long[] position = supports.iterator().next();
    assertEquals(100, position[0]);
    assertEquals(201, position[1]);
    assertEquals(300, position[2]);
  }

  @Test
  void doesNotExposeCoveredBlocks() {
    Ship ship = shipWith(new BlockPos(0, 0, 0), new BlockPos(0, 1, 0));
    Set<long[]> supports = DeckSurface.supportPositions(ship);
    assertEquals(1, supports.size());
    long[] position = supports.iterator().next();
    assertEquals(100, position[0]);
    assertEquals(202, position[1]);
    assertEquals(300, position[2]);
  }

  @Test
  void exposesTopOfEveryColumnAndAdjacentHoles() {
    Ship ship =
        shipWith(
            new BlockPos(0, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(0, 1, 0),
            new BlockPos(2, 0, 0));
    Set<long[]> supports = DeckSurface.supportPositions(ship);
    assertEquals(3, supports.size());
    assertTrue(supports.stream().anyMatch(p -> p[0] == 100 && p[1] == 202 && p[2] == 300));
    assertTrue(supports.stream().anyMatch(p -> p[0] == 101 && p[1] == 201 && p[2] == 300));
    assertTrue(supports.stream().anyMatch(p -> p[0] == 102 && p[1] == 201 && p[2] == 300));
  }

  @Test
  void neverSupportsOverOtherShipBlocks() {
    Ship ship = shipWith(new BlockPos(0, 0, 0), new BlockPos(0, 2, 0));
    Set<long[]> supports = DeckSurface.supportPositions(ship);
    // Both block tops are exposed: the y=0 block has air above it at y=1,
    // and the y=2 block has air above it at y=3. No support lands on a ship
    // block's own cell.
    assertEquals(2, supports.size());
    assertTrue(supports.stream().anyMatch(p -> p[0] == 100 && p[1] == 201 && p[2] == 300));
    assertTrue(supports.stream().anyMatch(p -> p[0] == 100 && p[1] == 203 && p[2] == 300));
  }

  private static Ship shipWith(BlockPos... positions) {
    ShipOrigin origin =
        new ShipOrigin(UUID.fromString("00000000-0000-0000-0000-000000000001"), 100, 200, 300);
    List<ShipBlock> blocks =
        java.util.Arrays.stream(positions)
            .map(pos -> new ShipBlock(pos, "minecraft:stone"))
            .toList();
    return new Ship(UUID.randomUUID(), UUID.randomUUID(), origin, blocks);
  }
}
