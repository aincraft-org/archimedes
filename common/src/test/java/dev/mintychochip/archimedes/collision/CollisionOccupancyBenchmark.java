package dev.mintychochip.archimedes.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A vs B speed and memory. A keeps every exposed cell live. B keeps the rider neighborhood.
 *
 * <p>{@code tickMove} is the per-tick work that scales with live cubes (Bukkit teleport). {@code
 * tickReconcile} is occupancy bookkeeping: A re-applies the full cell set, B re-queries edge
 * distance.
 */
class CollisionOccupancyBenchmark {
  private static final UUID WORLD = UUID.randomUUID();
  private static final ShipPose ZERO = new ShipPose(0, 0, 0);
  private static final int WARMUP = 200;
  private static final int SAMPLES = 1000;

  @Test
  void reportsSpeedAndMemoryAgainstFullSpawn() {
    print(measure(20, 5, 8, 9.2, 5.0, 3.2));
    print(measure(40, 10, 16, 19.2, 10.0, 7.2));
    Row small = measure(20, 5, 8, 9.2, 5.0, 3.2);
    assertTrue(small.bLive < small.aLive);
    assertEquals(0, CollisionStreamer.of(boxHull(20, 5, 8)).liveCount());
  }

  private static void print(Row row) {
    System.out.printf(
        "COLLISION_BENCH hull=%s live A=%d B=%d | fill A=%.3fms B=%.3fms"
            + " | tickReconcile A=%.3fms B=%.3fms"
            + " | tickMove A=%.3fms B=%.3fms | heap A=%dB B=%dB%n",
        row.hull,
        row.aLive,
        row.bLive,
        row.aFillNs / 1e6,
        row.bFillNs / 1e6,
        row.aReconcileNs / 1e6,
        row.bReconcileNs / 1e6,
        row.aMoveNs / 1e6,
        row.bMoveNs / 1e6,
        row.aHeap,
        row.bHeap);
  }

  private static Row measure(int dx, int dy, int dz, double px, double py, double pz) {
    Vehicle hull = boxHull(dx, dy, dz);
    CollisionObserver rider = riderAt(px, py, pz);
    CollisionObserver whole = covering(hull);
    CollisionStreamer warm = CollisionStreamer.of(hull);
    warm.observe(List.of(whole), ZERO);
    warm.observe(List.of(rider), ZERO);

    CollisionStreamer a = CollisionStreamer.of(hull);
    long aFill = time(() -> a.observe(List.of(whole), ZERO));
    int aLive = a.liveCount();
    long aReconcile = timeMany(SAMPLES, () -> a.observe(List.of(whole), ZERO));
    long aMove = timeMany(SAMPLES, () -> touch(a.live()));
    long aHeap = heapDelta(() -> occupy(hull, whole));

    CollisionStreamer b = CollisionStreamer.of(hull);
    long bFill = time(() -> b.observe(List.of(rider), ZERO));
    int bLive = b.liveCount();
    long bReconcile = timeMany(SAMPLES, () -> b.observe(List.of(rider), ZERO));
    long bMove = timeMany(SAMPLES, () -> touch(b.live()));
    long bHeap = heapDelta(() -> occupy(hull, rider));

    return new Row(
        dx + "x" + dy + "x" + dz,
        aLive,
        bLive,
        aFill,
        bFill,
        aReconcile,
        bReconcile,
        aMove,
        bMove,
        aHeap,
        bHeap);
  }

  private static CollisionObserver riderAt(double x, double y, double z) {
    return new CollisionObserver(
        UUID.randomUUID(), true, new CollisionBox(x, y, z, x + 0.6, y + 1.8, z + 0.6));
  }

  private static CollisionObserver covering(Vehicle hull) {
    CollisionBox bounds = ExposedCellIndex.build(hull).bounds(0, 0, 0);
    CollisionBox cover = bounds.expanded(1024);
    return new CollisionObserver(UUID.randomUUID(), true, cover);
  }

  private static CollisionStreamer occupy(Vehicle hull, CollisionObserver observer) {
    CollisionStreamer streamer = CollisionStreamer.of(hull);
    streamer.observe(List.of(observer), ZERO);
    return streamer;
  }

  private static int touch(Set<BlockPos> cells) {
    int sum = 0;
    for (BlockPos cell : cells) {
      sum += cell.x() + cell.y() + cell.z();
    }
    return sum;
  }

  private static long time(Runnable action) {
    action.run();
    long start = System.nanoTime();
    action.run();
    return System.nanoTime() - start;
  }

  private static long timeMany(int samples, Runnable action) {
    for (int i = 0; i < WARMUP; i++) {
      action.run();
    }
    long start = System.nanoTime();
    for (int i = 0; i < samples; i++) {
      action.run();
    }
    return (System.nanoTime() - start) / samples;
  }

  private static long heapDelta(java.util.function.Supplier<Object> retain) {
    gc();
    long before = used();
    Object held = retain.get();
    gc();
    long after = used();
    if (held == null) {
      throw new IllegalStateException("benchmark retain");
    }
    return Math.max(0L, after - before);
  }

  private static void gc() {
    Runtime.getRuntime().gc();
    Runtime.getRuntime().gc();
  }

  private static long used() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  private static Vehicle boxHull(int dx, int dy, int dz) {
    List<BlockPos> blocks = new ArrayList<>();
    for (int x = 0; x < dx; x++) {
      for (int y = 0; y < dy; y++) {
        for (int z = 0; z < dz; z++) {
          if (x == 0 || x == dx - 1 || y == 0 || y == dy - 1 || z == 0 || z == dz - 1) {
            blocks.add(new BlockPos(x, y, z));
          }
        }
      }
    }
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(WORLD, 0, 0, 0),
        blocks.stream().map(position -> new ShipBlock(position, "minecraft:stone")).toList());
  }

  private record Row(
      String hull,
      int aLive,
      int bLive,
      long aFillNs,
      long bFillNs,
      long aReconcileNs,
      long bReconcileNs,
      long aMoveNs,
      long bMoveNs,
      long aHeap,
      long bHeap) {}
}
