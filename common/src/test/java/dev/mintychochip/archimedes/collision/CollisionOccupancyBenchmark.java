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
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Occupancy A/B numbers for the streamed hull. Timing is reported, not asserted. */
class CollisionOccupancyBenchmark {
  private static final UUID WORLD = UUID.randomUUID();
  private static final ShipPose ZERO = new ShipPose(0, 0, 0);

  @Test
  void reportsOccupancyAgainstFullSpawn() {
    Row small = measure(20, 5, 8, 9.2, 5.0, 3.2);
    Row large = measure(40, 10, 16, 19.2, 10.0, 7.2);
    System.out.printf(
        "COLLISION_BENCH hull=20x5x8 A=%d B1=%d B2=%d empty=%d observe1=%.3fms observe2=%.3fms%n",
        small.a, small.bOne, small.bTwo, small.empty, small.observeOneMs, small.observeTwoMs);
    System.out.printf(
        "COLLISION_BENCH hull=40x10x16 A=%d B1=%d B2=%d empty=%d observe1=%.3fms observe2=%.3fms%n",
        large.a, large.bOne, large.bTwo, large.empty, large.observeOneMs, large.observeTwoMs);
    assertTrue(small.bOne < small.a);
    assertTrue(small.bTwo < small.a);
    assertTrue(small.bTwo > small.bOne);
    assertEquals(0, small.empty);
    assertTrue(large.bOne < large.a);
    assertEquals(0, large.empty);
  }

  private static Row measure(int dx, int dy, int dz, double px, double py, double pz) {
    Vehicle hull = boxHull(dx, dy, dz);
    CollisionStreamer streamer = CollisionStreamer.of(hull);
    int a = streamer.exposed();
    UUID one = UUID.randomUUID();
    UUID two = UUID.randomUUID();
    CollisionObserver mid = playerAt(one, px, py, pz);
    CollisionObserver bow = playerAt(one, 0.2, py, pz);
    CollisionObserver stern = playerAt(two, dx - 1.8, py, pz);
    long start = System.nanoTime();
    for (int i = 0; i < 1000; i++) {
      streamer.observe(List.of(mid), ZERO);
    }
    double observeOneMs = (System.nanoTime() - start) / 1_000_000.0 / 1000.0;
    int bOne = streamer.liveCount();
    start = System.nanoTime();
    for (int i = 0; i < 1000; i++) {
      streamer.observe(List.of(bow, stern), ZERO);
    }
    double observeTwoMs = (System.nanoTime() - start) / 1_000_000.0 / 1000.0;
    int bTwo = streamer.liveCount();
    streamer.observe(List.of(), ZERO);
    return new Row(a, bOne, bTwo, streamer.liveCount(), observeOneMs, observeTwoMs);
  }

  private static CollisionObserver playerAt(UUID id, double x, double y, double z) {
    return new CollisionObserver(id, true, new CollisionBox(x, y, z, x + 0.6, y + 1.8, z + 0.6));
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
      int a, int bOne, int bTwo, int empty, double observeOneMs, double observeTwoMs) {}
}
