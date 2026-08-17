package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.List;

/** Scans a connected component, returning null when it exceeds limits. */
@FunctionalInterface
public interface ComponentScanner {
  /**
   * Returns the component containing the seed, or null when invalid.
   *
   * @param x the seed x coordinate
   * @param y the seed y coordinate
   * @param z the seed z coordinate
   * @return the component relative positions, or null when invalid
   */
  List<BlockPos> scan(int x, int y, int z);
}
