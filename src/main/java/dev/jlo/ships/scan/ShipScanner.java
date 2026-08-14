package dev.jlo.ships.scan;

import dev.jlo.ships.model.BlockPos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

/**
 * Captures a face-connected, material-valid component using a bounded
 * six-directional breadth-first traversal. Scanning never mutates the world.
 */
public final class ShipScanner {
  private static final int[] DX = {1, -1, 0, 0, 0, 0};
  private static final int[] DY = {0, 0, 1, -1, 0, 0};
  private static final int[] DZ = {0, 0, 0, 0, 1, -1};

  private ShipScanner() {}

  /**
   * Scans the component containing {@code seed} up to {@code maximumBlocks}
   * blocks. Forbidden material names are lowered before comparison. Returns an
   * incomplete result with no captured list when the component exceeds the
   * limit or contains a forbidden material.
   */
  public static ScanResult scan(ScannerWorld world, Seed seed, int maximumBlocks, Set<String> forbidden) {
    Set<Long> visited = new HashSet<>();
    Deque<int[]> queue = new ArrayDeque<>();
    List<BlockPos> captured = new ArrayList<>();
    queue.add(new int[] {seed.x(), seed.y(), seed.z()});
    visited.add(pack(seed.x(), seed.y(), seed.z()));
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
        long key = pack(nx, ny, nz);
        if (visited.add(key)) {
          queue.add(new int[] {nx, ny, nz});
        }
      }
    }
    return new ScanResult(seed.x(), seed.y(), seed.z(), captured);
  }

  private static long pack(int x, int y, int z) {
    return ((long) x << 32) ^ (Integer.toUnsignedLong(y) << 16) ^ Integer.toUnsignedLong(z);
  }
}