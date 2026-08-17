package dev.mintychochip.archimedes.phys.bukkit;

import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.Vector3;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;

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
  public boolean isFluid(Vector3 point) {
    int x = (int) Math.floor(point.x());
    int y = (int) Math.floor(point.y());
    int z = (int) Math.floor(point.z());
    return fluids.contains(world.getBlockAt(x, y, z).getType());
  }

  @Override
  public double density(Vector3 point) {
    return isFluid(point) ? fluidDensity : 0.0;
  }
}
