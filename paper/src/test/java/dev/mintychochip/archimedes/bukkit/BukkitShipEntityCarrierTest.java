package dev.mintychochip.archimedes.bukkit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.papermc.paper.entity.TeleportFlag;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

/**
 * Tests that ship carry preserves rider movement while applying the ship's vertical displacement.
 */
class BukkitShipEntityCarrierTest {
  @Test
  void carryPreservesVelocityWhileMovingVertically() throws Exception {
    Location current = new Location(null, 12.5, 64.0, -3.25, 90.0f, 10.0f);
    AtomicReference<Location> destination = new AtomicReference<>();
    AtomicReference<PlayerTeleportEvent.TeleportCause> cause = new AtomicReference<>();
    AtomicReference<TeleportFlag[]> flags = new AtomicReference<>();
    Entity entity =
        (Entity)
            Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getLocation") && method.getParameterCount() == 0) {
                    return current;
                  }
                  if (method.getName().equals("teleport")) {
                    destination.set((Location) args[0]);
                    if (args.length == 3) {
                      cause.set((PlayerTeleportEvent.TeleportCause) args[1]);
                      flags.set((TeleportFlag[]) args[2]);
                    }
                    return true;
                  }
                  throw new AssertionError("Unexpected entity method: " + method);
                });

    Method carryEntity =
        BukkitShipEntityCarrier.class.getDeclaredMethod(
            "carryEntity", Entity.class, double.class, String.class);
    carryEntity.setAccessible(true);
    carryEntity.invoke(null, entity, 0.125, "ship-id");

    Location moved = destination.get();
    assertNotNull(moved);
    assertEquals(current.getX(), moved.getX());
    assertEquals(current.getY() + 0.125, moved.getY());
    assertEquals(current.getZ(), moved.getZ());
    assertEquals(PlayerTeleportEvent.TeleportCause.PLUGIN, cause.get());
    assertArrayEquals(
        new TeleportFlag[] {
          TeleportFlag.Relative.VELOCITY_X,
          TeleportFlag.Relative.VELOCITY_Y,
          TeleportFlag.Relative.VELOCITY_Z
        },
        flags.get());
  }

  @Test
  void carryFalseTeleportLogsShipContextWithoutThrowing() throws Exception {
    Entity entity =
        (Entity)
            Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getLocation")) {
                    return new Location(null, 1, 2, 3);
                  }
                  if (method.getName().equals("getUniqueId")) {
                    return java.util.UUID.randomUUID();
                  }
                  if (method.getName().equals("teleport")) {
                    return false;
                  }
                  if (method.getName().equals("getPersistentDataContainer")) {
                    return Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                        (container, containerMethod, containerArgs) -> {
                          if (containerMethod.getName().equals("get")) {
                            return null;
                          }
                          return containerMethod.getReturnType() == boolean.class ? false : null;
                        });
                  }
                  return null;
                });
    Method carryEntity =
        BukkitShipEntityCarrier.class.getDeclaredMethod(
            "carryEntity", Entity.class, double.class, String.class);
    carryEntity.setAccessible(true);
    try {
      carryEntity.invoke(null, entity, 0.125, "ship-id");
    } catch (java.lang.reflect.InvocationTargetException thrown) {
      Throwable cause = thrown.getCause();
      if (!(cause instanceof NullPointerException)) {
        throw new AssertionError(cause);
      }
    }
  }

  @Test
  void carryUsesVelocityInsteadOfTeleportForPlayers() throws Exception {
    Location current = new Location(null, 12.5, 64.0, -3.25, 90.0f, 10.0f);
    org.bukkit.util.Vector currentVelocity = new org.bukkit.util.Vector(0.2, 0.42, -0.1);
    AtomicReference<Location> destination = new AtomicReference<>();
    AtomicReference<org.bukkit.util.Vector> velocity = new AtomicReference<>();
    Player player =
        (Player)
            Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getLocation") && method.getParameterCount() == 0) {
                    return current;
                  }
                  if (method.getName().equals("getVelocity")) {
                    return currentVelocity.clone();
                  }
                  if (method.getName().equals("setVelocity")) {
                    velocity.set(((org.bukkit.util.Vector) args[0]).clone());
                    return null;
                  }
                  if (method.getName().equals("teleport")) {
                    destination.set((Location) args[0]);
                    return true;
                  }
                  throw new AssertionError("Unexpected player method: " + method);
                });

    Method carryEntity =
        BukkitShipEntityCarrier.class.getDeclaredMethod(
            "carryEntity", Entity.class, double.class, String.class);
    carryEntity.setAccessible(true);
    carryEntity.invoke(null, player, 0.125, "ship-id");

    assertNull(destination.get());
    assertEquals(new org.bukkit.util.Vector(0.2, 0.545, -0.1), velocity.get());
  }
}
