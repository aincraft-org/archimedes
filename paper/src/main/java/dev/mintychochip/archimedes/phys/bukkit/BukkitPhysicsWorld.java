package dev.mintychochip.archimedes.phys.bukkit;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.World;
import org.bukkit.Material;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Bukkit-backed physics world. Chunk occupancy is a {@link org.bukkit.World#isChunkLoaded(int,
 * int)} cache lookup, not {@code getChunkAt}, which would load from disk.
 */
public final class BukkitPhysicsWorld implements World {
  /** Bukkit world used for chunk cache and block samples. */
  private final org.bukkit.World world;

  /** Gravity vector from ship config. */
  private final Vector3d gravity;

  /** Fluid sampler. */
  private final FluidField fluidField;

  /** Integration timestep in seconds. */
  private final double timeStep;

  /**
   * Creates a physics world over a Bukkit world.
   *
   * @param world Bukkit world
   * @param config gravity and tick interval
   * @param fluidField liquid sampler
   */
  public BukkitPhysicsWorld(org.bukkit.World world, ShipConfig config, FluidField fluidField) {
    this.world = world;
    this.gravity = new Vector3d(0, -config.gravity(), 0);
    this.fluidField = fluidField;
    this.timeStep = config.physicsTicks() * 0.05;
  }

  @Override
  public Vector3dc gravity() {
    return gravity;
  }

  @Override
  public FluidField fluidField() {
    return fluidField;
  }

  @Override
  public double timeStep() {
    return timeStep;
  }

  @Override
  public boolean isObstacle(Vector3dc point) {
    return !passable(blockAt(point));
  }

  @Override
  public double vegetation(Vector3dc point) {
    return vegetation(blockAt(point)) ? 1.0 : 0.0;
  }

  @Override
  public boolean isChunkLoaded(int chunkX, int chunkZ) {
    return world.isChunkLoaded(chunkX, chunkZ);
  }

  private Material blockAt(Vector3dc point) {
    int x = (int) Math.floor(point.x());
    int y = (int) Math.floor(point.y());
    int z = (int) Math.floor(point.z());
    return world.getBlockAt(x, y, z).getType();
  }

  private static boolean passable(Material type) {
    return type == Material.AIR
        || type == Material.CAVE_AIR
        || type == Material.VOID_AIR
        || type == Material.WATER
        || type == Material.BUBBLE_COLUMN
        || vegetation(type);
  }

  private static boolean vegetation(Material type) {
    if (type == Material.KELP
        || type == Material.KELP_PLANT
        || type == Material.SEAGRASS
        || type == Material.TALL_SEAGRASS
        || type == Material.SEA_PICKLE
        || type == Material.SHORT_GRASS
        || type == Material.TALL_GRASS
        || type == Material.FERN
        || type == Material.LARGE_FERN
        || type == Material.DEAD_BUSH
        || type == Material.DANDELION
        || type == Material.POPPY
        || type == Material.SNOW
        || type == Material.MOSS_CARPET) {
      return true;
    }
    String name = type.name();
    return name.endsWith("_TULIP")
        || name.endsWith("_ORCHID")
        || name.endsWith("_BLOSSOM")
        || name.endsWith("_DAISY")
        || name.equals("ALLIUM")
        || name.equals("AZURE_BLUET")
        || name.equals("CORNFLOWER")
        || name.equals("LILY_OF_THE_VALLEY")
        || name.equals("TORCHFLOWER")
        || name.equals("PINK_PETALS")
        || name.equals("WILDFLOWERS");
  }
}
