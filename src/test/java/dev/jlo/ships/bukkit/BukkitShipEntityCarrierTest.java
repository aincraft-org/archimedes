package dev.jlo.ships.bukkit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.papermc.paper.entity.TeleportFlag;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
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
        BukkitShipEntityCarrier.class.getDeclaredMethod("carryEntity", Entity.class, double.class);
    carryEntity.setAccessible(true);
    carryEntity.invoke(null, entity, 0.125);

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
}
