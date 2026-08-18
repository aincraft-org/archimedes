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
    int x = (int) Math.floor(point.x());
    int y = (int) Math.floor(point.y());
    int z = (int) Math.floor(point.z());
    Material type = world.getBlockAt(x, y, z).getType();
    return !type.isAir() && type != Material.WATER;
  }

  @Override
  public boolean isChunkLoaded(int chunkX, int chunkZ) {
    return world.isChunkLoaded(chunkX, chunkZ);
  }
}
