package dev.jlo.ships.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.junit.jupiter.api.Test;

class BukkitCollisionVolumeManagerTest {
  private static final String NAMESPACE = "ships";
  private static final String COLLISION_KEY = "collision";
  private static final String STONE = "minecraft:stone";

  @Test
  void spawnConfiguresTaggedInvisibleInvulnerableNonPersistentCollisionAtCanonicalAnchor() {
    UUID shipId = UUID.randomUUID();
    java.util.Map<String, Object> values = new java.util.HashMap<>();
    java.util.Set<String> tags = new java.util.HashSet<>();
    org.bukkit.persistence.PersistentDataContainer data =
        proxy(
            org.bukkit.persistence.PersistentDataContainer.class,
            (ignored, method, args) -> {
              if ("set".equals(method.getName())) {
                values.put(args[0].toString(), args[2]);
              }
              if ("has".equals(method.getName())) {
                return values.containsKey(args[0].toString());
              }
              return defaultValue(method.getReturnType());
            });
    Shulker shulker =
        proxy(
            Shulker.class,
            (ignored, method, args) -> {
              String name = method.getName();
              if ("getPersistentDataContainer".equals(name)) return data;
              if ("addScoreboardTag".equals(name)) return tags.add((String) args[0]);
              if ("getLocation".equals(name)) return new Location(null, 100.5, 66.25, 200.5);
              if ("remove".equals(name)) return null;
              if (method.getReturnType() == boolean.class) return true;
              return defaultValue(method.getReturnType());
            });
    Ship ship =
        new Ship(
            shipId,
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 100, 64, 200),
            List.of(new ShipBlock(new BlockPos(0, 1, 0), STONE)),
            new dev.jlo.ships.model.ShipPose(1.25),
            true);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(
            worldSpawning(shulker), new NamespacedKey(NAMESPACE, COLLISION_KEY));

    manager.spawn(ship);

  }

  @Test
  void moveSkipsTeleportWithinSameAuthoritativeFloorAndMovesAcrossBoundary() {
    UUID shipId = UUID.randomUUID();
    java.util.List<Location> teleports = new java.util.ArrayList<>();
    Shulker shulker =
        proxy(
            Shulker.class,
            (ignored, method, args) -> {
              if ("getLocation".equals(method.getName())) return new Location(null, 100.5, 64.25, 200.5);
              if ("teleport".equals(method.getName())) {
                teleports.add((Location) args[0]);
                return true;
              }
              if ("getPersistentDataContainer".equals(method.getName())) {
                return proxy(
                    org.bukkit.persistence.PersistentDataContainer.class,
                    (container, containerMethod, containerArgs) -> defaultValue(containerMethod.getReturnType()));
              }
              if (method.getReturnType() == boolean.class) return true;
              return defaultValue(method.getReturnType());
            });
    Ship ship =
        new Ship(
            shipId,
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 100, 64, 200),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)),
            new dev.jlo.ships.model.ShipPose(0.25),
            true);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(
            worldSpawning(shulker), new NamespacedKey(NAMESPACE, COLLISION_KEY));
    manager.spawn(ship);

    ship.setPose(new dev.jlo.ships.model.ShipPose(0.75));
    manager.move(ship);
    assertEquals(0, teleports.size());

    ship.setPose(new dev.jlo.ships.model.ShipPose(1.0));
    manager.move(ship);
    assertEquals(1, teleports.size());
    assertEquals(65.0, teleports.get(0).getY());
  }

  @Test
  void rollbackReturnsEveryVolumeToOldCanonicalAnchor() {
    UUID shipId = UUID.randomUUID();
    java.util.List<Location> teleports = new java.util.ArrayList<>();
    Shulker shulker =
        proxy(
            Shulker.class,
            (ignored, method, args) -> {
              if ("getLocation".equals(method.getName())) return new Location(null, 100.5, 64.0, 200.5);
              if ("teleport".equals(method.getName())) {
                teleports.add((Location) args[0]);
                return true;
              }
              if ("getPersistentDataContainer".equals(method.getName())) {
                return proxy(
                    org.bukkit.persistence.PersistentDataContainer.class,
                    (container, containerMethod, containerArgs) -> defaultValue(containerMethod.getReturnType()));
              }
              if (method.getReturnType() == boolean.class) return true;
              return defaultValue(method.getReturnType());
            });
    Ship ship =
        new Ship(
            shipId,
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 100, 64, 200),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)),
            new dev.jlo.ships.model.ShipPose(1.0),
            true);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(
            worldSpawning(shulker), new NamespacedKey(NAMESPACE, COLLISION_KEY));
    manager.spawn(ship);

    manager.rollback(ship, 0.0);

    assertEquals(1, teleports.size());
    assertEquals(64.0, teleports.get(0).getY());
  }

  @SuppressWarnings("PMD.AvoidDuplicateLiterals")
  @Test
  void removeAllContinuesAfterTaggedMetadataFailure() {
    RuntimeException metadata = new IllegalStateException("metadata");
    java.util.concurrent.atomic.AtomicInteger removals =
        new java.util.concurrent.atomic.AtomicInteger();
    Shulker first =
        proxy(
            Shulker.class,
            (ignored, method, args) -> {
              if (method.getName().equals("getPersistentDataContainer")) throw metadata;
              return defaultValue(method.getReturnType());
            });
    Shulker second =
        proxy(
            Shulker.class,
            (ignored, method, args) -> {
              if (method.getName().equals("remove")) {
                removals.incrementAndGet();
                return null;
              }
              if (method.getName().equals("getPersistentDataContainer")) {
                return proxy(
                    org.bukkit.persistence.PersistentDataContainer.class,
                    (container, containerMethod, containerArgs) ->
                        containerMethod.getName().equals("has")
                            ? true
                            : defaultValue(containerMethod.getReturnType()));
              }
              return defaultValue(method.getReturnType());
            });
    World world =
        proxy(
            World.class,
            (ignored, method, args) ->
                method.getName().equals("getEntitiesByClass")
                    ? List.of(first, second)
                    : defaultValue(method.getReturnType()));

    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey(NAMESPACE, COLLISION_KEY));

    ShipRuntimeException thrown = assertThrows(ShipRuntimeException.class, manager::removeAll);
    assertSame(metadata, thrown.getCause());
    assertEquals(1, removals.get());
  }

  @SuppressWarnings("PMD.AvoidDuplicateLiterals")
  @Test
  void spawnNormalizesGenericRuntimeAndCleansEveryLocal() {
    UUID shipId = UUID.randomUUID();
    RuntimeException failure = new IllegalStateException("spawn");
    Shulker first = shulkerThatThrowsOnRemove(new IllegalArgumentException("cleanup"));
    World world = worldSpawningThenFails(first, failure);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey(NAMESPACE, COLLISION_KEY));

    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> manager.spawn(shipWithTwoBlocks(shipId)));
    assertSame(failure, thrown.getCause());
    assertTrue(thrown.getMessage().contains(shipId.toString()));
    assertTrue(thrown.getSuppressed().length > 0);
  }

  @Test
  void spawnPreservesShipRuntimeExceptionAndCleansLocals() {
    UUID shipId = UUID.randomUUID();
    ShipRuntimeException failure = new ShipRuntimeException("spawn", null);
    Shulker first = shulkerThatThrowsOnRemove(new IllegalArgumentException("cleanup"));
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(
            worldSpawningThenFails(first, failure), new NamespacedKey(NAMESPACE, COLLISION_KEY));

    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> manager.spawn(shipWithTwoBlocks(shipId)));

    assertSame(failure, thrown);
    assertTrue(thrown.getSuppressed().length > 0);
  }

  @Test
  void moveWrapsRuntimeTeleportFailureWithShipContext() {
    UUID shipId = UUID.randomUUID();
    RuntimeException teleport = new IllegalStateException("teleport");
    Shulker shulker = shulkerThatThrows(teleport);
    World world = worldSpawning(shulker);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey(NAMESPACE, COLLISION_KEY));
    Ship ship = ship(shipId);
    ship.setPose(new dev.jlo.ships.model.ShipPose(1.0));
    manager.spawn(ship);

    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> manager.move(ship));
    assertSame(teleport, thrown.getCause().getCause());
    assertTrue(thrown.getCause().getMessage().contains(shipId.toString()));
  }

  @Test
  void removeAttemptsBothVolumesAndSuppressesSecondFailure() {
    UUID shipId = UUID.randomUUID();
    RuntimeException first = new IllegalStateException("first");
    RuntimeException second = new IllegalArgumentException("second");
    List<Integer> attempts = new java.util.ArrayList<>();
    Shulker one = shulkerThatThrowsOnRemove(first, attempts);
    Shulker two = shulkerThatThrowsOnRemove(second, attempts);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(
            worldSpawning(List.of(one, two)), new NamespacedKey(NAMESPACE, COLLISION_KEY));
    manager.spawn(shipWithTwoBlocks(shipId));
    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> manager.remove(shipId));
    assertEquals(2, attempts.size());
    assertEquals(1, thrown.getSuppressed().length);
  }

  private static Ship ship(UUID shipId) {
    return new Ship(
        shipId,
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
  }

  private static Ship shipWithTwoBlocks(UUID shipId) {
    return new Ship(
        shipId,
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(
            new ShipBlock(new BlockPos(0, 0, 0), STONE),
            new ShipBlock(new BlockPos(1, 0, 0), STONE)));
  }

  private static Shulker shulkerThatThrowsOnRemove(RuntimeException failure) {
    return shulkerThatThrowsOnRemove(failure, new java.util.ArrayList<>());
  }

  private static Shulker shulkerThatThrowsOnRemove(
      RuntimeException failure, List<Integer> attempts) {
    return (Shulker)
        Proxy.newProxyInstance(
            Shulker.class.getClassLoader(),
            new Class<?>[] {Shulker.class},
            (proxy, method, args) -> {
              if (method.getName().equals("remove")) {
                attempts.add(1);
                throw failure;
              }
              if (method.getName().equals("getLocation")) return new Location(null, 0, 0, 0);
              if (method.getName().equals("getPersistentDataContainer")) {
                return Proxy.newProxyInstance(
                    BukkitCollisionVolumeManagerTest.class.getClassLoader(),
                    new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                    (container, containerMethod, containerArgs) -> null);
              }
              if (method.getReturnType() == boolean.class) return true;
              return null;
            });
  }

  private static World worldSpawningThenFails(Shulker first, RuntimeException failure) {
    return (World)
        Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            new java.lang.reflect.InvocationHandler() {
              private boolean spawned;

              @Override
              public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
                  throws Throwable {
                if (method.getName().equals("spawn") && args != null && args.length == 3) {
                  if (spawned) throw failure;
                  spawned = true;
                  return first;
                }
                if (method.getReturnType() == boolean.class) return false;
                return null;
              }
            });
  }

  private static Shulker shulkerThatThrows(RuntimeException failure) {
    return (Shulker)
        Proxy.newProxyInstance(
            Shulker.class.getClassLoader(),
            new Class<?>[] {Shulker.class},
            (proxy, method, args) -> {
              if (method.getName().equals("teleport")) throw failure;
              if (method.getName().equals("getLocation")) return new Location(null, 0, 0, 0);
              if (method.getName().equals("getPersistentDataContainer")) {
                return Proxy.newProxyInstance(
                    BukkitCollisionVolumeManagerTest.class.getClassLoader(),
                    new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                    (container, containerMethod, containerArgs) -> {
                      if ("set".equals(containerMethod.getName())) return null;
                      return containerMethod.getReturnType() == boolean.class ? false : null;
                    });
              }
              if (method.getReturnType() == boolean.class) return true;
              return null;
            });
  }

  private static World worldSpawning(Shulker shulker) {
    return worldSpawning(List.of(shulker));
  }

  private static World worldSpawning(List<Shulker> shulkers) {
    return (World)
        Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            new java.lang.reflect.InvocationHandler() {
              private int index;

              @Override
              public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                if (method.getName().equals("spawn") && args != null && args.length == 3) {
                  return shulkers.get(Math.min(index++, shulkers.size() - 1));
                }
                if (method.getReturnType() == boolean.class) return false;
                return null;
              }
            });
  }

  @Test
  void moveNormalizesLocationSnapshotFailureWithShipContext() {
    RuntimeException snapshotFailure = new RuntimeException("snapshot failed");
    Shulker shulker =
        proxy(
            Shulker.class,
            (ignored, method, args) -> {
              if (method.getName().equals("getLocation")) {
                throw snapshotFailure;
              }
              return defaultValue(method.getReturnType());
            });
    World world =
        proxy(
            World.class,
            (ignored, method, args) ->
                method.getName().equals("spawn") ? shulker : defaultValue(method.getReturnType()));
    NamespacedKey ownerKey = new NamespacedKey("ships", "collision");
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));

    BukkitCollisionVolumeManager manager = new BukkitCollisionVolumeManager(world, ownerKey);
    manager.spawn(ship);

    ShipRuntimeException failure =
        assertThrows(ShipRuntimeException.class, () -> manager.move(ship));
    IllegalStateException context =
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    assertEquals("Collision move failed for ship " + ship.id(), context.getMessage());
    assertSame(snapshotFailure, context.getCause());
  }

  private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    if (type == byte.class || type == short.class || type == int.class || type == long.class) {
      return 0;
    }
    if (type == float.class || type == double.class) {
      return 0.0;
    }
    return null;
  }
}
