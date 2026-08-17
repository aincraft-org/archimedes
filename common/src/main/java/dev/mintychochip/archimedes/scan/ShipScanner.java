package dev.mintychochip.archimedes.scan;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Captures a face-connected, material-valid component using a bounded six-directional breadth-first
 * traversal. Scanning never mutates the world.
 */
public final class ShipScanner {
  /** X deltas for the six face directions. */
  private static final int[] DX = {1, -1, 0, 0, 0, 0};

  /** Y deltas for the six face directions. */
  private static final int[] DY = {0, 0, 1, -1, 0, 0};

  /** Z deltas for the six face directions. */
  private static final int[] DZ = {0, 0, 0, 0, 1, -1};

  private ShipScanner() {}

  /**
   * Scans the face-connected component containing the seed, up to {@code maximumBlocks} captured
   * blocks. A forbidden seed returns an incomplete result; forbidden neighboring blocks are not
   * captured or traversed. Captured blocks are connected through faces only. A non-positive {@code
   * maximumBlocks} causes the first non-air captured block to exceed the limit and returns an
   * incomplete result. Forbidden material names are lowered before comparison.
   *
   * @param world the world to scan
   * @param seed the seed block
   * @param maximumBlocks the maximum captured blocks
   * @param forbidden forbidden material registry names
   * @return an incomplete result with no captured list when invalid
   */
  public static ScanResult scan(
      ScannerWorld world, Seed seed, int maximumBlocks, Set<String> forbidden) {
    Set<CoordKey> visited = new HashSet<>();
    Deque<int[]> queue = new ArrayDeque<>();
    List<BlockPos> captured = new ArrayList<>();
    queue.add(new int[] {seed.x(), seed.y(), seed.z()});
    visited.add(new CoordKey(seed.x(), seed.y(), seed.z()));
    while (!queue.isEmpty()) {
      int[] current = queue.poll();
      int x = current[0];
      int y = current[1];
      int z = current[2];
      if (!world.airAt(x, y, z)) {
        captured.add(new BlockPos(x - seed.x(), y - seed.y(), z - seed.z()));
        if (captured.size() > maximumBlocks) {
          return new ScanResult();
        }
        String material = world.materialAt(x, y, z);
        if (forbidden.contains(material.toLowerCase(Locale.ROOT))) {
          return new ScanResult();
        }
      }
      for (int i = 0; i < 6; i++) {
        int nx = x + DX[i];
        int ny = y + DY[i];
        int nz = z + DZ[i];
        if (world.airAt(nx, ny, nz)) {
          continue;
        }
        String material = world.materialAt(nx, ny, nz).toLowerCase(Locale.ROOT);
        if (forbidden.contains(material)) {
          continue;
        }
        if (visited.add(new CoordKey(nx, ny, nz))) {
          queue.add(new int[] {nx, ny, nz});
        }
      }
    }
    return new ScanResult(seed.x(), seed.y(), seed.z(), captured);
  }
}
