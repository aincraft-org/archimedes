package dev.mintychochip.archimedes.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
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
}
