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

/** Paper-free occupancy algorithm: edge-distance query, hysteresis, share-or-create, refcount. */
class CollisionStreamerTest {
  private static final UUID WORLD = UUID.randomUUID();
  private static final BlockPos ORIGIN_CELL = new BlockPos(0, 0, 0);
  private static final ShipPose ZERO = new ShipPose(0, 0, 0);

  @Test
  void overlappingObserverCreatesTheCell() {
    CollisionStreamer streamer = CollisionStreamer.of(ship(List.of(ORIGIN_CELL)));
    UUID player = UUID.randomUUID();
    CollisionVolumePool.Diff diff =
        streamer.observe(List.of(playerAt(player, 100.2, 200.2, 300.2)), ZERO);
    assertEquals(Set.of(ORIGIN_CELL), diff.spawn());
    assertEquals(Set.of(player), diff.show().get(ORIGIN_CELL));
    assertEquals(1, streamer.liveCount());
  }

  @Test
  void secondObserverSharesWithoutCreating() {
    CollisionStreamer streamer = CollisionStreamer.of(ship(List.of(ORIGIN_CELL)));
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    streamer.observe(List.of(playerAt(first, 100.2, 200.2, 300.2)), ZERO);
    CollisionVolumePool.Diff diff =
        streamer.observe(
            List.of(playerAt(first, 100.2, 200.2, 300.2), playerAt(second, 100.4, 200.2, 300.4)),
            ZERO);
    assertEquals(Set.of(), diff.spawn());
    assertEquals(Set.of(second), diff.show().get(ORIGIN_CELL));
    assertEquals(2, streamer.refcount(ORIGIN_CELL));
  }

  @Test
  void lastObserverLeavingDestroysTheCell() {
    CollisionStreamer streamer = CollisionStreamer.of(ship(List.of(ORIGIN_CELL)));
    UUID player = UUID.randomUUID();
    streamer.observe(List.of(playerAt(player, 100.2, 200.2, 300.2)), ZERO);
    CollisionVolumePool.Diff diff = streamer.observe(List.of(), ZERO);
    assertEquals(Set.of(ORIGIN_CELL), diff.despawn());
    assertEquals(0, streamer.liveCount());
  }

  @Test
  void hysteresisKeepsAHeldCellBetweenEnterAndLeave() {
    CollisionStreamer streamer = CollisionStreamer.of(ship(List.of(ORIGIN_CELL)));
    UUID player = UUID.randomUUID();
    streamer.observe(List.of(playerAt(player, 100.2, 200.2, 300.2)), ZERO);
    CollisionVolumePool.Diff stay =
        streamer.observe(List.of(playerAt(player, 106.0, 200.0, 300.0)), ZERO);
    assertEquals(Set.of(), stay.despawn());
    assertEquals(1, streamer.liveCount());
    CollisionVolumePool.Diff gone =
        streamer.observe(List.of(playerAt(player, 120.0, 200.0, 300.0)), ZERO);
    assertEquals(Set.of(ORIGIN_CELL), gone.despawn());
  }

  @Test
  void farObserversDoNotShareAndStayBelowFullHull() {
    CollisionStreamer streamer = CollisionStreamer.of(boxHull());
    int exposed = streamer.exposed();
    UUID bow = UUID.randomUUID();
    UUID stern = UUID.randomUUID();
    streamer.observe(List.of(playerAt(bow, 0.2, 5.0, 3.2), playerAt(stern, 18.2, 5.0, 3.2)), ZERO);
    assertTrue(streamer.liveCount() < exposed, "B=" + streamer.liveCount() + " A=" + exposed);
    assertTrue(streamer.liveCount() > 0);
    assertEquals(exposed, CollisionHull.exposedBlocks(boxHull()).size());
  }

  @Test
  void emptyObserversMatchControlAWithZeroLive() {
    CollisionStreamer streamer = CollisionStreamer.of(boxHull());
    int aLive = streamer.exposed();
    streamer.observe(List.of(), ZERO);
    assertTrue(aLive > 0);
    assertEquals(0, streamer.liveCount());
  }

  @Test
  void nonPlayerObserverCreatesWithoutShow() {
    CollisionStreamer streamer = CollisionStreamer.of(ship(List.of(ORIGIN_CELL)));
    UUID item = UUID.randomUUID();
    CollisionObserver observer =
        new CollisionObserver(
            item, false, new CollisionBox(100.2, 200.2, 300.2, 100.8, 200.4, 300.8));
    CollisionVolumePool.Diff diff = streamer.observe(List.of(observer), ZERO);
    assertEquals(Set.of(ORIGIN_CELL), diff.spawn());
    assertTrue(diff.show().isEmpty());
    assertEquals(1, streamer.liveCount());
  }

  private static CollisionObserver playerAt(UUID id, double x, double y, double z) {
    return new CollisionObserver(id, true, new CollisionBox(x, y, z, x + 0.6, y + 1.8, z + 0.6));
  }

  private static Vehicle ship(List<BlockPos> positions) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(WORLD, 100, 200, 300),
        positions.stream().map(position -> new ShipBlock(position, "minecraft:stone")).toList());
  }

  private static Vehicle boxHull() {
    List<BlockPos> blocks = new ArrayList<>();
    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 5; y++) {
        for (int z = 0; z < 8; z++) {
          if (x == 0 || x == 19 || y == 0 || y == 4 || z == 0 || z == 7) {
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
}
