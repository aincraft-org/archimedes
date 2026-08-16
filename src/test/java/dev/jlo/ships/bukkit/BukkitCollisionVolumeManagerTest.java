package dev.jlo.ships.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import dev.jlo.ships.ship.ShipRuntimeException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.junit.jupiter.api.Test;

/** Behavioral failure coverage for the Shulker collision adapter. */
class BukkitCollisionVolumeManagerTest {
  @Test
  void adapterCompilesAgainstConfiguredPaperApi() {
    assertTrue(BukkitCollisionVolumeManager.class.getName().contains("CollisionVolumeManager"));
  }

  @Test
  void spawnNormalizesRuntimeFailureAndCleansPartialEntities() {
    UUID shipId = UUID.randomUUID();
    AtomicInteger spawned = new AtomicInteger();
    AtomicInteger removed = new AtomicInteger();
    RuntimeException cause = new IllegalStateException("spawn boom");
    World world = worldProxy(spawned, removed, cause);
    Ship ship = shipWithTwoBlocks(shipId);
    ShipRuntimeException failure =
        assertThrows(
            ShipRuntimeException.class,
            () ->
                new BukkitCollisionVolumeManager(world, new NamespacedKey("ships", "owner"))
                    .spawn(ship));
    assertEquals("Bukkit collision spawn failed for ship " + shipId, failure.getMessage());
    assertSame(cause, failure.getCause());
    assertTrue(spawned.get() >= 1);
    assertTrue(removed.get() >= 1);
  }

  @Test
  void cleanupFailureIsSuppressedOnPrimaryFailure() {
    RuntimeException spawnCause = new IllegalStateException("spawn boom");
    RuntimeException cleanupCause = new IllegalArgumentException("cleanup boom");
    World world = worldProxy(new AtomicInteger(), new AtomicInteger(), spawnCause, cleanupCause);
    ShipRuntimeException failure =
        assertThrows(
            ShipRuntimeException.class,
            () ->
                new BukkitCollisionVolumeManager(world, new NamespacedKey("ships", "owner"))
                    .spawn(shipWithTwoBlocks(UUID.randomUUID())));
    assertTrue(failure.getSuppressed().length > 0);
    assertSame(cleanupCause, failure.getSuppressed()[0].getCause());
  }

  private static Ship shipWithTwoBlocks(UUID id) {
    return new Ship(
        id,
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
        List.of(
            new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone"),
            new ShipBlock(new BlockPos(1, 0, 0), "minecraft:stone")));
  }

  private static Ship ship(UUID id) {
    return new Ship(
        id,
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
  }

  private static World worldProxy(
      AtomicInteger spawned, AtomicInteger removed, RuntimeException spawnFailure) {
    return worldProxy(spawned, removed, spawnFailure, null);
  }

  private static World worldProxy(
      AtomicInteger spawned,
      AtomicInteger removed,
      RuntimeException spawnFailure,
      RuntimeException cleanupFailure) {
    return (World)
        Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) -> {
              if ("spawn".equals(method.getName())) {
                if (spawned.incrementAndGet() > 1) {
                  throw spawnFailure;
                }
                return shulkerProxy(removed, cleanupFailure);
              }
              return defaultValue(method.getReturnType());
            });
  }

  private static Shulker shulkerProxy(AtomicInteger removed, RuntimeException cleanupFailure) {
    return (Shulker)
        Proxy.newProxyInstance(
            Shulker.class.getClassLoader(),
            new Class<?>[] {Shulker.class},
            (proxy, method, args) -> {
              if ("remove".equals(method.getName())) {
                removed.incrementAndGet();
                if (cleanupFailure != null) {
                  throw cleanupFailure;
                }
                return null;
              }
              if ("getPersistentDataContainer".equals(method.getName())) {
                return Proxy.newProxyInstance(
                    Shulker.class.getClassLoader(),
                    new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                    (p, m, a) -> defaultValue(m.getReturnType()));
              }
              return defaultValue(method.getReturnType());
            });
  }

  private static Object defaultValue(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == float.class) return 0.0f;
    if (type == double.class) return 0.0;
    return null;
  }
}
