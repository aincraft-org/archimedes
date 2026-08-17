package dev.mintychochip.archimedes.phys.bukkit;

import dev.mintychochip.phys.FluidField;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.joml.Vector3dc;

/** Bukkit-backed fluid field. */
public final class BukkitFluidField implements FluidField {
  /** Bukkit world queried for block types. */
  private final World world;

  /** Density returned for fluid blocks. */
  private final double fluidDensity;

  /** Materials considered fluid. */
  private final Set<Material> fluids;

  /**
   * Creates a fluid field backed by a Bukkit world.
   *
   * @param world world whose block types are queried
   * @param fluidDensity density returned for blocks classified as fluid
   */
  public BukkitFluidField(World world, double fluidDensity) {
    this.world = world;
    this.fluidDensity = fluidDensity;
    this.fluids = Set.of(Material.WATER);
  }

  /**
   * Tests whether the block containing a point is fluid.
   *
   * <p>Coordinates are floored before the corresponding Bukkit block is queried.
   *
   * @param point world-space point to test
   * @return {@code true} when the containing block is a configured fluid material
   */
  @Override
  public boolean isFluid(Vector3dc point) {
    int x = (int) Math.floor(point.x());
    int y = (int) Math.floor(point.y());
    int z = (int) Math.floor(point.z());
    return fluids.contains(world.getBlockAt(x, y, z).getType());
  }

  /**
   * Returns the configured density at a point.
   *
   * @param point world-space point to sample
   * @return the configured fluid density for fluid blocks, or {@code 0.0} otherwise
   */
  @Override
  public double density(Vector3dc point) {
    return isFluid(point) ? fluidDensity : 0.0;
  }
}
