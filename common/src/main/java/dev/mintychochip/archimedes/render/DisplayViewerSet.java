package dev.mintychochip.archimedes.render;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Per-viewer set of display cells that have line of sight from the eye through occupancy. */
public final class DisplayViewerSet {
  private DisplayViewerSet() {}

  /**
   * Returns the subset of {@code candidates} visible from the eye.
   *
   * @param occupied ship cells that occlude and may be shown
   * @param worldSolids extra solid world cells
   * @param eyeX eye x
   * @param eyeY eye y
   * @param eyeZ eye z
   * @param candidates cells that have a display
   * @return visible cells for this viewer
   */
  public static Set<BlockPos> visibleTo(
      Set<BlockPos> occupied,
      Set<BlockPos> worldSolids,
      double eyeX,
      double eyeY,
      double eyeZ,
      Collection<BlockPos> candidates) {
    Set<BlockPos> visible = new HashSet<>();
    for (BlockPos cell : candidates) {
      if (VoxelLos.hasLineOfSight(occupied, worldSolids, eyeX, eyeY, eyeZ, cell)) {
        visible.add(cell);
      }
    }
    return Set.copyOf(visible);
  }
}
