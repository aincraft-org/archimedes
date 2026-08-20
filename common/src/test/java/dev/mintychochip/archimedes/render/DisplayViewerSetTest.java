package dev.mintychochip.archimedes.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Per-viewer visible display cells from occupancy and line of sight. */
class DisplayViewerSetTest {
  private static final BlockPos WALL = new BlockPos(1, 0, 0);
  private static final BlockPos BACK = new BlockPos(2, 0, 0);
  private static final BlockPos SIDE = new BlockPos(0, 0, 2);

  @Test
  void openAirCellIsInTheViewerSet() {
    Set<BlockPos> occupied = Set.of(BACK);
    Set<BlockPos> visible = DisplayViewerSet.visibleTo(occupied, Set.of(), 0.5, 0.5, 0.5, occupied);
    assertEquals(Set.of(BACK), visible);
  }

  @Test
  void occludedCellIsCulled() {
    Set<BlockPos> occupied = Set.of(WALL, BACK);
    Set<BlockPos> visible = DisplayViewerSet.visibleTo(occupied, Set.of(), 0.5, 0.5, 0.5, occupied);
    assertTrue(visible.contains(WALL));
    assertFalse(visible.contains(BACK));
  }

  @Test
  void twoViewersSeeDifferentCells() {
    Set<BlockPos> occupied = Set.of(WALL, BACK, SIDE);
    Set<BlockPos> fromWest =
        DisplayViewerSet.visibleTo(occupied, Set.of(), -0.5, 0.5, 0.5, occupied);
    Set<BlockPos> fromEast =
        DisplayViewerSet.visibleTo(occupied, Set.of(), 3.5, 0.5, 0.5, occupied);
    assertTrue(fromWest.contains(WALL));
    assertFalse(fromWest.contains(BACK));
    assertTrue(fromEast.contains(BACK));
    assertFalse(fromEast.contains(WALL));
    assertFalse(fromWest.equals(fromEast));
  }

  @Test
  void standingOnAWideDeckKeepsTheFarCells() {
    HashSet<BlockPos> occupied = new HashSet<>();
    for (int x = 0; x < 5; x++) {
      for (int z = 0; z < 5; z++) {
        occupied.add(new BlockPos(x, 0, z));
      }
    }
    Set<BlockPos> visible =
        DisplayViewerSet.visibleTo(occupied, Set.of(), 2.5, 1.62, 2.5, occupied);
    assertEquals(occupied, visible);
  }
}
