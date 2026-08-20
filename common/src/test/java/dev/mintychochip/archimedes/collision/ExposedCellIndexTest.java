package dev.mintychochip.archimedes.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests AABB-to-AABB edge-distance selection of exposed hull cells. */
class ExposedCellIndexTest {
  private static final UUID WORLD = UUID.randomUUID();

  @Test
  void overlappingObserverNeedsTheCell() {
    ExposedCellIndex index = ExposedCellIndex.build(ship(List.of(new BlockPos(0, 0, 0))));
    CollisionBox observer = new CollisionBox(100.2, 200.2, 300.2, 100.8, 201.8, 300.8);
    assertEquals(List.of(new BlockPos(0, 0, 0)), index.cellsWithin(observer, 0, 0, 0, 4.0));
  }

  @Test
  void farObserverIsOutsideEnterRange() {
    ExposedCellIndex index = ExposedCellIndex.build(ship(List.of(new BlockPos(0, 0, 0))));
    CollisionBox observer = new CollisionBox(120, 200, 300, 120.6, 201.8, 300.6);
    assertEquals(List.of(), index.cellsWithin(observer, 0, 0, 0, 4.0));
  }

  @Test
  void hysteresisKeepsCellBetweenEnterAndLeave() {
    ExposedCellIndex index = ExposedCellIndex.build(ship(List.of(new BlockPos(0, 0, 0))));
    CollisionBox observer = new CollisionBox(106, 200, 300, 106.6, 201.8, 300.6);
    assertEquals(List.of(), index.cellsWithin(observer, 0, 0, 0, 4.0));
    assertEquals(List.of(new BlockPos(0, 0, 0)), index.cellsWithin(observer, 0, 0, 0, 6.0));
  }

  @Test
  void poseShiftMovesTheQueryBox() {
    ExposedCellIndex index = ExposedCellIndex.build(ship(List.of(new BlockPos(0, 0, 0))));
    CollisionBox observer = new CollisionBox(102.2, 200.2, 300.2, 102.8, 201.8, 300.8);
    assertEquals(List.of(), index.cellsWithin(observer, 0, 0, 0, 0.0));
    assertEquals(List.of(new BlockPos(0, 0, 0)), index.cellsWithin(observer, 2, 0, 0, 0.0));
  }

  private static Vehicle ship(List<BlockPos> positions) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(WORLD, 100, 200, 300),
        positions.stream().map(position -> new ShipBlock(position, "minecraft:stone")).toList());
  }
}
