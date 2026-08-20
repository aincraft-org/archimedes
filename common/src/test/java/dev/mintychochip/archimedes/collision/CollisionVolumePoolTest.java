package dev.mintychochip.archimedes.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests refcounted sharing of collision cells across observers. */
class CollisionVolumePoolTest {
  private static final BlockPos CELL = new BlockPos(0, 1, 0);

  @Test
  void firstPlayerObserverSpawnsAndShows() {
    CollisionVolumePool pool = new CollisionVolumePool();
    UUID player = UUID.randomUUID();
    CollisionVolumePool.Diff diff = pool.reconcile(Map.of(player, Set.of(CELL)), Set.of(player));
    assertEquals(Set.of(CELL), diff.spawn());
    assertEquals(Set.of(), diff.despawn());
    assertEquals(Set.of(player), diff.show().get(CELL));
    assertEquals(1, pool.refcount(CELL));
    assertEquals(Set.of(CELL), pool.live());
  }

  @Test
  void secondPlayerSharesWithoutSpawning() {
    CollisionVolumePool pool = new CollisionVolumePool();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    pool.reconcile(Map.of(first, Set.of(CELL)), Set.of(first));
    CollisionVolumePool.Diff diff =
        pool.reconcile(Map.of(first, Set.of(CELL), second, Set.of(CELL)), Set.of(first, second));
    assertEquals(Set.of(), diff.spawn());
    assertEquals(Set.of(), diff.despawn());
    assertEquals(Set.of(second), diff.show().get(CELL));
    assertEquals(2, pool.refcount(CELL));
  }

  @Test
  void lastObserverDespawns() {
    CollisionVolumePool pool = new CollisionVolumePool();
    UUID player = UUID.randomUUID();
    pool.reconcile(Map.of(player, Set.of(CELL)), Set.of(player));
    CollisionVolumePool.Diff diff = pool.reconcile(Map.of(), Set.of());
    assertEquals(Set.of(CELL), diff.despawn());
    assertEquals(0, pool.refcount(CELL));
    assertEquals(Set.of(), pool.live());
  }

  @Test
  void nonPlayerObserverSpawnsWithoutShow() {
    CollisionVolumePool pool = new CollisionVolumePool();
    UUID item = UUID.randomUUID();
    CollisionVolumePool.Diff diff = pool.reconcile(Map.of(item, Set.of(CELL)), Set.of());
    assertEquals(Set.of(CELL), diff.spawn());
    assertTrue(diff.show().isEmpty());
    assertEquals(1, pool.refcount(CELL));
  }

  @Test
  void identicalReconcileIsEmpty() {
    CollisionVolumePool pool = new CollisionVolumePool();
    UUID player = UUID.randomUUID();
    Map<UUID, Set<BlockPos>> desired = Map.of(player, Set.of(CELL));
    pool.reconcile(desired, Set.of(player));
    CollisionVolumePool.Diff diff = pool.reconcile(desired, Set.of(player));
    assertEquals(Set.of(), diff.spawn());
    assertEquals(Set.of(), diff.despawn());
    assertTrue(diff.show().isEmpty());
    assertTrue(diff.hide().isEmpty());
  }
}
