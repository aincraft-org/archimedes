package dev.mintychochip.archimedes.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.collision.CollisionBox;
import dev.mintychochip.archimedes.collision.CollisionMode;
import dev.mintychochip.archimedes.collision.CollisionObserver;
import dev.mintychochip.archimedes.collision.CollisionSnapshot;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.ship.ShipRuntimeException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Shulker;
import org.junit.jupiter.api.Test;

class BukkitCollisionVolumeManagerTest {
  private static final String NAMESPACE = "archimedes";
  private static final String COLLISION_KEY = "collision";
  private static final String STONE = "minecraft:stone";
  private static final String GET_PERSISTENT_DATA_CONTAINER = "getPersistentDataContainer";
  private static final String SPAWN = "spawn";
  private static final String SPAWNED_AT_KEY = "spawn";
  private static final String GET_LOCATION = "getLocation";

  @Test
  void spawnConfiguresTaggedInvisibleInvulnerableNonPersistentCollisionAtCanonicalAnchor() {
    UUID shipId = UUID.randomUUID();
    java.util.Map<String, Object> values = new java.util.HashMap<>();
    java.util.Map<String, Location> spawnedAt = new java.util.HashMap<>();
    java.util.Set<String> tags = new java.util.HashSet<>();
    org.bukkit.persistence.PersistentDataContainer data =
        proxy(
            org.bukkit.persistence.PersistentDataContainer.class,
            (ignored, method, args) -> {
              if ("set".equals(method.getName())) {
                values.put(args[0].toString(), args[2]);
              }
              return defaultValue(method.getReturnType());
            });
    Shulker shulker =
        proxy(
            Shulker.class,
            (ignored, method, args) -> {
              String name = method.getName();
              if (GET_PERSISTENT_DATA_CONTAINER.equals(name)) return data;
              if ("addScoreboardTag".equals(name)) {
                tags.add((String) args[0]);
                return true;
              }
              if (name.startsWith("set")) {
                values.put(name, args[0]);
                return null;
              }
              if ("remove".equals(name)) return null;
              return defaultValue(method.getReturnType());
            });
    World world =
        proxy(
            World.class,
            (ignored, method, args) -> {
              if (SPAWN.equals(method.getName()) && args.length == 3) {
                spawnedAt.put(SPAWNED_AT_KEY, (Location) args[0]);
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<Shulker> callback =
                    (java.util.function.Consumer<Shulker>) args[2];
                callback.accept(shulker);
                return shulker;
              }
              return defaultValue(method.getReturnType());
            });
    Vehicle ship =
        new Vehicle(
            shipId,
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 100, 64, 200),
            List.of(new ShipBlock(new BlockPos(0, 1, 0), STONE)),
            new dev.mintychochip.archimedes.model.ShipPose(1.25),
            true);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey(NAMESPACE, COLLISION_KEY));
    spawnFull(manager, ship);
    assertEquals(100.5, spawnedAt.get(SPAWNED_AT_KEY).getX());
    assertEquals(66.25, spawnedAt.get(SPAWNED_AT_KEY).getY());
    assertEquals(200.5, spawnedAt.get(SPAWNED_AT_KEY).getZ());
    assertEquals(false, values.get("setAI"));
    assertEquals(true, values.get("setInvisible"));
    assertEquals(true, values.get("setInvulnerable"));
    assertEquals(true, values.get("setSilent"));
    assertEquals(false, values.get("setGravity"));
    assertEquals(true, values.get("setCollidable"));
    assertEquals(0.0f, values.get("setPeek"));
    assertEquals(false, values.get("setPersistent"));
    assertEquals(shipId.toString(), values.get("archimedes:collision"));
    assertEquals("0,1,0", values.get("archimedes:collision-block"));
    assertTrue(tags.contains("archimedes-collision-" + shipId));
    assertEquals(100.5, spawnedAt.get(SPAWNED_AT_KEY).getX());
    assertEquals(66.25, spawnedAt.get(SPAWNED_AT_KEY).getY());
    assertEquals(200.5, spawnedAt.get(SPAWNED_AT_KEY).getZ());
  }

  @Test
  void moveNegativeFractionalFloorsSkipThenTeleportEveryVolumeOnce() {
    UUID shipId = UUID.randomUUID();
    java.util.List<Location> oneTeleports = new java.util.ArrayList<>();
    java.util.List<Location> twoTeleports = new java.util.ArrayList<>();
    Shulker one = recordingShulker(new Location(null, 0.5, -1.75, 0.5), oneTeleports);
    Shulker two = recordingShulker(new Location(null, 1.5, -1.75, 0.5), twoTeleports);
    Vehicle ship = shipWithTwoBlocks(shipId);
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(-1.75));
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(
            worldSpawning(List.of(one, two)), new NamespacedKey(NAMESPACE, COLLISION_KEY));
    spawnFull(manager, ship);

    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(-1.25));
    manager.move(ship);
    assertEquals(1, oneTeleports.size(), "fractional pose must move the solid deck");
    assertEquals(1, twoTeleports.size());
    assertEquals(-1.25, oneTeleports.get(0).getY(), 1e-9);

    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(-0.75));
    manager.move(ship);
    assertEquals(2, oneTeleports.size());
    assertEquals(2, twoTeleports.size());
    assertEquals(0.5, oneTeleports.get(1).getX());
    assertEquals(-0.75, oneTeleports.get(1).getY());
    assertEquals(0.5, oneTeleports.get(1).getZ());
    assertEquals(1.5, twoTeleports.get(1).getX());
    assertEquals(-0.75, twoTeleports.get(1).getY());
    assertEquals(0.5, twoTeleports.get(1).getZ());
  }

  @Test
  void rollbackReturnsAllVolumesToExactOldAnchors() {
    UUID shipId = UUID.randomUUID();
    java.util.List<Location> teleports = new java.util.ArrayList<>();
    Shulker one = recordingShulker(new Location(null, 0.5, 1.5, 0.5), teleports);
    Shulker two = recordingShulker(new Location(null, 1.5, 1.5, 0.5), teleports);
    Vehicle ship = shipWithTwoBlocks(shipId);
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(1.5));
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(
            worldSpawning(List.of(one, two)), new NamespacedKey(NAMESPACE, COLLISION_KEY));
    spawnFull(manager, ship);

    manager.rollback(ship, 0.25);

    assertEquals(
        java.util.Set.of("0.5,0.25,0.5", "1.5,0.25,0.5"),
        teleports.stream()
            .map(location -> location.getX() + "," + location.getY() + "," + location.getZ())
            .collect(java.util.stream.Collectors.toSet()));
  }

  // Task 8 report: negative fractional movement uses tolerance-based location verification.

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
              if (GET_PERSISTENT_DATA_CONTAINER.equals(method.getName())) throw metadata;
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
              if (GET_PERSISTENT_DATA_CONTAINER.equals(method.getName())) {
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
    RuntimeException failure = new IllegalStateException(SPAWN);
    Shulker first = shulkerThatThrowsOnRemove(new IllegalArgumentException("cleanup"));
    World world = worldSpawningThenFails(first, failure);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey(NAMESPACE, COLLISION_KEY));

    Vehicle ship = shipWithTwoBlocks(shipId);
    manager.setMode(ship, CollisionMode.FULL);
    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> manager.spawn(ship));
    assertSame(failure, thrown.getCause());
    assertTrue(thrown.getMessage().contains(shipId.toString()));
    assertTrue(thrown.getSuppressed().length > 0);
  }

  @Test
  void spawnPreservesShipRuntimeExceptionAndCleansLocals() {
    UUID shipId = UUID.randomUUID();
    ShipRuntimeException failure = new ShipRuntimeException(SPAWN, null);
    Shulker first = shulkerThatThrowsOnRemove(new IllegalArgumentException("cleanup"));
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(
            worldSpawningThenFails(first, failure), new NamespacedKey(NAMESPACE, COLLISION_KEY));

    Vehicle ship = shipWithTwoBlocks(shipId);
    manager.setMode(ship, CollisionMode.FULL);
    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> manager.spawn(ship));

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
    Vehicle ship = ship(shipId);
    ship.setPose(new dev.mintychochip.archimedes.model.ShipPose(1.0));
    spawnFull(manager, ship);

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
    spawnFull(manager, shipWithTwoBlocks(shipId));
    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> manager.remove(shipId));
    assertEquals(2, attempts.size());
    assertEquals(1, thrown.getSuppressed().length);
  }

  @Test
  void streamedSpawnCreatesNoEntities() {
    java.util.concurrent.atomic.AtomicInteger spawns =
        new java.util.concurrent.atomic.AtomicInteger();
    World world =
        proxy(
            World.class,
            (ignored, method, args) -> {
              if (SPAWN.equals(method.getName())) {
                spawns.incrementAndGet();
              }
              return defaultValue(method.getReturnType());
            });
    Vehicle ship = ship(UUID.randomUUID());
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey(NAMESPACE, COLLISION_KEY));
    manager.spawn(ship);
    assertEquals(0, spawns.get());
    CollisionSnapshot snapshot = manager.snapshot(ship.id(), UUID.randomUUID());
    assertEquals(CollisionMode.STREAMED, snapshot.mode());
    assertEquals(0, snapshot.live());
    assertEquals(1, snapshot.exposed());
  }

  @Test
  void streamedObserveSpawnsOverlappingCellAtCanonicalAnchor() {
    java.util.Map<String, Object> values = new java.util.HashMap<>();
    java.util.Map<String, Location> spawnedAt = new java.util.HashMap<>();
    org.bukkit.persistence.PersistentDataContainer data =
        proxy(
            org.bukkit.persistence.PersistentDataContainer.class,
            (ignored, method, args) -> {
              if ("set".equals(method.getName())) {
                values.put(args[0].toString(), args[2]);
              }
              return defaultValue(method.getReturnType());
            });
    Shulker shulker =
        proxy(
            Shulker.class,
            (ignored, method, args) -> {
              String name = method.getName();
              if (GET_PERSISTENT_DATA_CONTAINER.equals(name)) return data;
              if (name.startsWith("set")) {
                values.put(name, args[0]);
                return null;
              }
              if ("remove".equals(name)) return null;
              return defaultValue(method.getReturnType());
            });
    World world =
        proxy(
            World.class,
            (ignored, method, args) -> {
              if (SPAWN.equals(method.getName()) && args.length == 3) {
                spawnedAt.put(SPAWNED_AT_KEY, (Location) args[0]);
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<Shulker> callback =
                    (java.util.function.Consumer<Shulker>) args[2];
                callback.accept(shulker);
                return shulker;
              }
              return defaultValue(method.getReturnType());
            });
    UUID shipId = UUID.randomUUID();
    Vehicle ship =
        new Vehicle(
            shipId,
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 100, 64, 200),
            List.of(new ShipBlock(new BlockPos(0, 1, 0), STONE)),
            new dev.mintychochip.archimedes.model.ShipPose(1.25),
            true);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey(NAMESPACE, COLLISION_KEY));
    manager.spawn(ship);
    UUID player = UUID.randomUUID();
    manager.observe(
        ship,
        List.of(
            new CollisionObserver(
                player, true, new CollisionBox(100.2, 66.3, 200.2, 100.8, 68.0, 200.8))));
    assertEquals(100.5, spawnedAt.get(SPAWNED_AT_KEY).getX());
    assertEquals(66.25, spawnedAt.get(SPAWNED_AT_KEY).getY());
    assertEquals(200.5, spawnedAt.get(SPAWNED_AT_KEY).getZ());
    assertEquals(false, values.get("setVisibleByDefault"));
    CollisionSnapshot snapshot = manager.snapshot(shipId, player);
    assertEquals(1, snapshot.live());
    assertEquals(1, snapshot.visibleToPlayer());
    manager.observe(ship, List.of());
    assertEquals(0, manager.snapshot(shipId, player).live());
  }

  private static void spawnFull(BukkitCollisionVolumeManager manager, Vehicle ship) {
    manager.setMode(ship, CollisionMode.FULL);
    manager.spawn(ship);
  }

  private static Vehicle ship(UUID shipId) {
    return new Vehicle(
        shipId,
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
  }

  private static Vehicle shipWithTwoBlocks(UUID shipId) {
    return new Vehicle(
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
              if (GET_LOCATION.equals(method.getName())) return new Location(null, 0, 0, 0);
              if (GET_PERSISTENT_DATA_CONTAINER.equals(method.getName())) {
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
                if (SPAWN.equals(method.getName()) && args != null && args.length == 3) {
                  if (spawned) throw failure;
                  spawned = true;
                  return first;
                }
                if (method.getReturnType() == boolean.class) return false;
                return null;
              }
            });
  }

  private static Shulker recordingShulker(Location initial, java.util.List<Location> teleports) {
    return proxy(
        Shulker.class,
        (ignored, method, args) -> {
          if (GET_LOCATION.equals(method.getName())) return initial;
          if ("teleport".equals(method.getName())) {
            teleports.add((Location) args[0]);
            return true;
          }
          if (GET_PERSISTENT_DATA_CONTAINER.equals(method.getName())) {
            return proxy(
                org.bukkit.persistence.PersistentDataContainer.class,
                (container, containerMethod, containerArgs) ->
                    defaultValue(containerMethod.getReturnType()));
          }
          if (method.getReturnType() == boolean.class) return true;
          return defaultValue(method.getReturnType());
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
                if (SPAWN.equals(method.getName()) && args != null && args.length == 3) {
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
              if (GET_LOCATION.equals(method.getName())) {
                throw snapshotFailure;
              }
              return defaultValue(method.getReturnType());
            });
    World world =
        proxy(
            World.class,
            (ignored, method, args) ->
                method.getName().equals(SPAWN) ? shulker : defaultValue(method.getReturnType()));
    NamespacedKey ownerKey = new NamespacedKey("archimedes", "collision");
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));

    BukkitCollisionVolumeManager manager = new BukkitCollisionVolumeManager(world, ownerKey);
    spawnFull(manager, ship);

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
