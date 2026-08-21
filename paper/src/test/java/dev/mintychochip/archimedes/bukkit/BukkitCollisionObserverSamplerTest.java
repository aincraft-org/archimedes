package dev.mintychochip.archimedes.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.collision.CollisionObserver;
import dev.mintychochip.archimedes.collision.ExposedCellIndex;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/** Tests nearby-entity sampling for streamed collision observers. */
class BukkitCollisionObserverSamplerTest {
  private static final String NAMESPACE = "archimedes";

  @Test
  void playerInsideExpandedBoundsIsAPlayerObserver() {
    UUID playerId = UUID.randomUUID();
    Player player =
        entity(
            Player.class,
            playerId,
            new BoundingBox(100.2, 200.2, 300.2, 100.8, 201.8, 300.8),
            null);
    World world = worldWith(List.of(player));
    Vehicle ship = ship(List.of(new BlockPos(0, 0, 0)));
    BukkitCollisionObserverSampler sampler =
        new BukkitCollisionObserverSampler(
            world,
            new NamespacedKey(NAMESPACE, "collision"),
            new NamespacedKey(NAMESPACE, "ship-id"));
    List<CollisionObserver> observers = sampler.sample(ship, ExposedCellIndex.build(ship));
    assertEquals(1, observers.size());
    assertEquals(playerId, observers.get(0).id());
    assertTrue(observers.get(0).player());
  }

  @Test
  void itemInsideBoundsIsSpawnOnly() {
    UUID itemId = UUID.randomUUID();
    Item item =
        entity(Item.class, itemId, new BoundingBox(100.2, 200.2, 300.2, 100.8, 200.4, 300.8), null);
    World world = worldWith(List.of(item));
    Vehicle ship = ship(List.of(new BlockPos(0, 0, 0)));
    BukkitCollisionObserverSampler sampler =
        new BukkitCollisionObserverSampler(
            world,
            new NamespacedKey(NAMESPACE, "collision"),
            new NamespacedKey(NAMESPACE, "ship-id"));
    List<CollisionObserver> observers = sampler.sample(ship, ExposedCellIndex.build(ship));
    assertEquals(1, observers.size());
    assertEquals(itemId, observers.get(0).id());
    assertFalse(observers.get(0).player());
  }

  @Test
  void taggedCollisionEntityIsSkipped() {
    Entity shulker =
        entity(
            Entity.class,
            UUID.randomUUID(),
            new BoundingBox(100.2, 200.2, 300.2, 100.8, 201.8, 300.8),
            "ship");
    World world = worldWith(List.of(shulker));
    Vehicle ship = ship(List.of(new BlockPos(0, 0, 0)));
    BukkitCollisionObserverSampler sampler =
        new BukkitCollisionObserverSampler(
            world,
            new NamespacedKey(NAMESPACE, "collision"),
            new NamespacedKey(NAMESPACE, "ship-id"));
    assertEquals(List.of(), sampler.sample(ship, ExposedCellIndex.build(ship)));
  }

  private static Vehicle ship(List<BlockPos> positions) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 100, 200, 300),
        positions.stream().map(position -> new ShipBlock(position, "minecraft:stone")).toList());
  }

  private static World worldWith(Collection<Entity> nearby) {
    return (World)
        Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) -> {
              if ("getNearbyEntities".equals(method.getName())) {
                return nearby;
              }
              return defaultValue(method.getReturnType());
            });
  }

  private static <T extends Entity> T entity(
      Class<T> type, UUID id, BoundingBox box, String owner) {
    org.bukkit.persistence.PersistentDataContainer data =
        (org.bukkit.persistence.PersistentDataContainer)
            Proxy.newProxyInstance(
                org.bukkit.persistence.PersistentDataContainer.class.getClassLoader(),
                new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                (proxy, method, args) -> {
                  if ("get".equals(method.getName())) {
                    return owner;
                  }
                  return defaultValue(method.getReturnType());
                });
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> {
              String name = method.getName();
              if ("getUniqueId".equals(name)) {
                return id;
              }
              if ("getBoundingBox".equals(name)) {
                return box;
              }
              if ("getPersistentDataContainer".equals(name)) {
                return data;
              }
              return defaultValue(method.getReturnType());
            }));
  }

  private static Object defaultValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (!type.isPrimitive()) {
      return null;
    }
    return 0;
  }
}
