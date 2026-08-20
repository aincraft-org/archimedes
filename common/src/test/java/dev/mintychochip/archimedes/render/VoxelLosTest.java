package dev.mintychochip.archimedes.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Voxel DDA line-of-sight through occupied ship cells and world solids. */
class VoxelLosTest {
  private static final BlockPos TARGET = new BlockPos(2, 0, 0);
  private static final BlockPos WALL = new BlockPos(1, 0, 0);

  @Test
  void emptyAirToTargetIsVisible() {
    Set<BlockPos> occupied = Set.of(TARGET);
    assertTrue(VoxelLos.hasLineOfSight(occupied, Set.of(), 0.5, 0.5, 0.5, TARGET));
  }

  @Test
  void occupiedCellOnTheRayOccludes() {
    Set<BlockPos> occupied = Set.of(WALL, TARGET);
    assertFalse(VoxelLos.hasLineOfSight(occupied, Set.of(), 0.5, 0.5, 0.5, TARGET));
  }

  @Test
  void worldSolidOnTheRayOccludes() {
    Set<BlockPos> occupied = Set.of(TARGET);
    assertFalse(VoxelLos.hasLineOfSight(occupied, Set.of(WALL), 0.5, 0.5, 0.5, TARGET));
  }

  @Test
  void theTargetCellItselfIsNotAnOccluder() {
    Set<BlockPos> occupied = Set.of(TARGET);
    assertTrue(VoxelLos.hasLineOfSight(occupied, Set.of(), 1.6, 0.5, 0.5, TARGET));
  }

  @Test
  void standingAboveAWideDeckSeesTheFarCorner() {
    Set<BlockPos> occupied = deck(5);
    BlockPos corner = new BlockPos(0, 0, 0);
    assertTrue(VoxelLos.hasLineOfSight(occupied, Set.of(), 2.5, 1.62, 2.5, corner));
  }

  @Test
  void farSideOfAOneThickWallStaysHidden() {
    Set<BlockPos> occupied = Set.of(WALL, TARGET);
    assertFalse(VoxelLos.hasLineOfSight(occupied, Set.of(), 0.5, 0.5, 0.5, TARGET));
  }

  @Test
  void hollowHullDoesNotShowTheInteriorBackWallFromOutside() {
    Set<BlockPos> occupied = hollowBox();
    BlockPos back = new BlockPos(2, 1, 1);
    assertFalse(VoxelLos.hasLineOfSight(occupied, Set.of(), -0.5, 1.5, 1.5, back));
    assertTrue(VoxelLos.hasLineOfSight(occupied, Set.of(), -0.5, 1.5, 1.5, new BlockPos(0, 1, 1)));
  }

  private static Set<BlockPos> deck(int size) {
    HashSet<BlockPos> cells = new HashSet<>();
    for (int x = 0; x < size; x++) {
      for (int z = 0; z < size; z++) {
        cells.add(new BlockPos(x, 0, z));
      }
    }
    return cells;
  }

  private static Set<BlockPos> hollowBox() {
    HashSet<BlockPos> cells = new HashSet<>();
    for (int x = 0; x < 3; x++) {
      for (int y = 0; y < 3; y++) {
        for (int z = 0; z < 3; z++) {
          if (x == 0 || x == 2 || y == 0 || y == 2 || z == 0 || z == 2) {
            cells.add(new BlockPos(x, y, z));
          }
        }
      }
    }
    return cells;
  }
}
