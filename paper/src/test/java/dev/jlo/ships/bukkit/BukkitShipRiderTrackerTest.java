package dev.jlo.ships.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

class BukkitShipRiderTrackerTest {
  @Test
  void eventOverlapUsesStoredSuppliedBasisInsteadOfMutableShipPose() {
    UUID worldId = UUID.randomUUID();
    UUID entityId = UUID.randomUUID();
    Ship ship = ship(worldId);
    ship.setPose(new dev.jlo.ships.model.ShipPose(20));
    Entity entity = entity(entityId);
    World world = world(worldId, entity);
    BukkitShipRiderTracker tracker =
        new BukkitShipRiderTracker(
            world,
            () -> List.of(ship),
            new NamespacedKey("test", "collision"),
            new NamespacedKey("test", "render"));

    tracker.track(ship, 4.0);
    tracker.onEntityMove(newMoveEvent(entity, new Location(world, 0.5, 5.01, 0.5)));

    assertEquals(List.of(entityId), List.copyOf(tracker.riders(ship)));
  }

  private static io.papermc.paper.event.entity.EntityMoveEvent newMoveEvent(
      Entity entity, Location to) {
    return new io.papermc.paper.event.entity.EntityMoveEvent(
        (org.bukkit.entity.LivingEntity) entity, new Location(to.getWorld(), 0, 0, 0), to);
  }

  private static Ship ship(UUID worldId) {
    return new Ship(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(worldId, 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
  }

  private static World world(UUID id, Entity entity) {
    return (World)
        Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getUID" -> id;
                  case "equals" -> proxy == args[0];
                  case "getNearbyEntities" -> List.of(entity);
                  default -> null;
                });
  }

  private static org.bukkit.entity.LivingEntity entity(UUID id) {
    return (org.bukkit.entity.LivingEntity)
        Proxy.newProxyInstance(
            org.bukkit.entity.LivingEntity.class.getClassLoader(),
            new Class<?>[] {org.bukkit.entity.LivingEntity.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getUniqueId" -> id;
                  case "getBoundingBox" ->
                      new org.bukkit.util.BoundingBox(0.25, 5.01, 0.25, 0.75, 6.81, 0.75);
                  case "getWidth" -> 0.5;
                  case "getHeight" -> 1.8;
                  case "isValid" -> true;
                  case "isDead" -> false;
                  case "getVehicle" -> null;
                  case "getPersistentDataContainer" ->
                      Proxy.newProxyInstance(
                          BukkitShipRiderTrackerTest.class.getClassLoader(),
                          new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                          (container, containerMethod, containerArgs) -> null);
                  default -> null;
                });
  }
}
