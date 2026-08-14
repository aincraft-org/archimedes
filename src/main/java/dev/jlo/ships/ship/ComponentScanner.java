package dev.jlo.ships.ship;

import dev.jlo.ships.model.BlockPos;
import java.util.List;

/** Scans a connected component, returning null when it exceeds limits. */
@FunctionalInterface
public interface ComponentScanner {
  /** Returns the component containing the seed, or null when invalid. */
  List<BlockPos> scan(int x, int y, int z);
}