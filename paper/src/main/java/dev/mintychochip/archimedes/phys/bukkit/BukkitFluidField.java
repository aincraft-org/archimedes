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

  public BukkitFluidField(World world, double fluidDensity) {
    this.world = world;
    this.fluidDensity = fluidDensity;
    this.fluids = Set.of(Material.WATER);
  }

  @Override
  public boolean isFluid(Vector3dc point) {
    int x = (int) Math.floor(point.x());
    int y = (int) Math.floor(point.y());
    int z = (int) Math.floor(point.z());
    return fluids.contains(world.getBlockAt(x, y, z).getType());
  }

  @Override
  public double density(Vector3dc point) {
    return isFluid(point) ? fluidDensity : 0.0;
  }
}
