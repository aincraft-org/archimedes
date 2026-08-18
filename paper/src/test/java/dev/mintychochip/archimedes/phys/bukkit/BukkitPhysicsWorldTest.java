package dev.mintychochip.archimedes.phys.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.config.ShipConfig;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Adapter tests for chunk-cache occupancy. */
class BukkitPhysicsWorldTest {
  @Test
  void chunkOccupancyUsesIsChunkLoadedNeverGetChunkAt() {
    List<String> calls = new ArrayList<>();
    World bukkit =
        (World)
            Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, args) -> {
                  String name = method.getName();
                  if ("isChunkLoaded".equals(name) && args != null && args.length == 2) {
                    calls.add("isChunkLoaded:" + args[0] + "," + args[1]);
                    return true;
                  }
                  if ("getChunkAt".equals(name) || "loadChunk".equals(name)) {
                    calls.add(name);
                    return defaultValue(method.getReturnType());
                  }
                  return defaultValue(method.getReturnType());
                });
    BukkitPhysicsWorld world =
        new BukkitPhysicsWorld(bukkit, config(), new BukkitFluidField(bukkit, 1.0));

    assertTrue(world.isChunkLoaded(3, -2));
    assertEquals(List.of("isChunkLoaded:3,-2"), calls);
    assertFalse(calls.contains("getChunkAt"));
    assertFalse(calls.contains("loadChunk"));
  }

  @Test
  void reportsUnloadedChunksFromTheBukkitCache() {
    World bukkit =
        (World)
            Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, args) -> {
                  if ("isChunkLoaded".equals(method.getName())
                      && args != null
                      && args.length == 2) {
                    return (int) args[0] == 1 && (int) args[1] == 4;
                  }
                  return defaultValue(method.getReturnType());
                });
    BukkitPhysicsWorld world =
        new BukkitPhysicsWorld(bukkit, config(), new BukkitFluidField(bukkit, 1.0));
    assertTrue(world.isChunkLoaded(1, 4));
    assertFalse(world.isChunkLoaded(1, 5));
    assertEquals(0.05, world.timeStep(), 1e-9);
    assertTrue(world.gravity().y() < 0);
  }

  @Test
  void kelpAndSeagrassAreNotObstaclesAndReportVegetation() {
    BukkitPhysicsWorld kelp = worldOf(Material.KELP);
    BukkitPhysicsWorld plant = worldOf(Material.KELP_PLANT);
    BukkitPhysicsWorld grass = worldOf(Material.SEAGRASS);
    BukkitPhysicsWorld tall = worldOf(Material.TALL_SEAGRASS);
    Vector3d p = new Vector3d(1.5, 62.5, 3.5);
    assertFalse(kelp.isObstacle(p));
    assertFalse(plant.isObstacle(p));
    assertFalse(worldOf(Material.SHORT_GRASS).isObstacle(p));
    assertFalse(worldOf(Material.TALL_GRASS).isObstacle(p));
    assertFalse(grass.isObstacle(p));
    assertFalse(tall.isObstacle(p));
    assertEquals(1.0, kelp.vegetation(p), 0.0);
    assertEquals(1.0, plant.vegetation(p), 0.0);
    assertEquals(1.0, grass.vegetation(p), 0.0);
    assertEquals(1.0, tall.vegetation(p), 0.0);
  }

  @Test
  void stoneBlocksAndWaterDoesNotCountAsVegetation() {
    Vector3d p = new Vector3d(0.5, 10.5, 0.5);
    BukkitPhysicsWorld stone = worldOf(Material.STONE);
    BukkitPhysicsWorld water = worldOf(Material.WATER);
    assertTrue(stone.isObstacle(p));
    assertEquals(0.0, stone.vegetation(p), 0.0);
    assertFalse(water.isObstacle(p));
    assertEquals(0.0, water.vegetation(p), 0.0);
  }

  private static BukkitPhysicsWorld worldOf(Material type) {
    Block block =
        (Block)
            Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[] {Block.class},
                (proxy, method, args) -> {
                  if ("getType".equals(method.getName())) {
                    return type;
                  }
                  return defaultValue(method.getReturnType());
                });
    World bukkit =
        (World)
            Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, args) -> {
                  if ("getBlockAt".equals(method.getName())) {
                    return block;
                  }
                  return defaultValue(method.getReturnType());
                });
    return new BukkitPhysicsWorld(bukkit, config(), new BukkitFluidField(bukkit, 1.0));
  }

  private static ShipConfig config() {
    return new ShipConfig(64, 5, Set.of(), Set.of(), true, 1, 0.5, 16.0, 0.05, 1.0, 0.5, 0.9);
  }

  private static Object defaultValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == double.class) {
      return 0.0;
    }
    if (type == float.class) {
      return 0.0f;
    }
    return null;
  }
}
