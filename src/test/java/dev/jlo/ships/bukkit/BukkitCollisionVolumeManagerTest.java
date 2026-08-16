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
  void moveWrapsRuntimeTeleportFailureWithShipContext() {
    UUID shipId = UUID.randomUUID();
    RuntimeException teleport = new IllegalStateException("teleport");
    Shulker shulker = shulkerThatThrows(teleport);
    World world = worldSpawning(shulker);
    BukkitCollisionVolumeManager manager =
        new BukkitCollisionVolumeManager(world, new NamespacedKey("ships", "collision"));
    Ship ship = new Ship(shipId, UUID.randomUUID(), new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    manager.spawn(ship);

    ShipRuntimeException thrown = assertThrows(ShipRuntimeException.class, () -> manager.move(ship));
    assertSame(teleport, thrown.getCause().getCause());
    assertTrue(thrown.getCause().getMessage().contains(shipId.toString()));
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
