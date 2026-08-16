package dev.jlo.ships.bukkit;

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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Shulker;
import org.junit.jupiter.api.Test;

class BukkitCollisionVolumeManagerTest {
  @Test
  void spawnNormalizesGenericRuntimeAndCleansEveryLocal() {
    UUID shipId = UUID.randomUUID();
    RuntimeException failure = new IllegalStateException("spawn");
    Shulker first = shulkerThatThrowsOnRemove(new IllegalArgumentException("cleanup"));
    World world = worldSpawningThenFails(first, failure);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey("ships", "collision"));

    ShipRuntimeException thrown =
        assertThrows(
            ShipRuntimeException.class, () -> manager.spawn(shipWithTwoBlocks(shipId)));

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
            worldSpawningThenFails(first, failure), new NamespacedKey("ships", "collision"));

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
        new BukkitCollisionVolumeManager(world, new NamespacedKey("ships", "collision"));
    Ship ship = ship(shipId);
    manager.spawn(ship);

    ShipRuntimeException thrown = assertThrows(ShipRuntimeException.class, () -> manager.move(ship));
    assertSame(teleport, thrown.getCause().getCause());
    assertTrue(thrown.getCause().getMessage().contains(shipId.toString()));
  }
  private static Ship ship(UUID shipId) {
    return new Ship(
        shipId,
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
  }

  private static Ship shipWithTwoBlocks(UUID shipId) {
    return new Ship(
        shipId,
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(
            new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone"),
            new ShipBlock(new BlockPos(1, 0, 0), "minecraft:stone")));
  }

  private static Shulker shulkerThatThrowsOnRemove(RuntimeException failure) {
    return (Shulker)
        Proxy.newProxyInstance(
            Shulker.class.getClassLoader(),
            new Class<?>[] {Shulker.class},
            (proxy, method, args) -> {
              if (method.getName().equals("remove")) throw failure;
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
    return (Shulker) Proxy.newProxyInstance(Shulker.class.getClassLoader(), new Class<?>[] {Shulker.class},
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
    return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
        (proxy, method, args) -> {
          if (method.getName().equals("spawn") && args != null && args.length == 3) return shulker;
          if (method.getReturnType() == boolean.class) return false;
          return null;
        });
  }
}
