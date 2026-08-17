package dev.mintychochip.phys;

import org.bukkit.Material;
import org.bukkit.World;

/** Bukkit-backed buoyancy surface: reads water and air from the world. */
public final class BukkitBuoyancySurface implements BuoyancySurface {
  /** The Bukkit world being queried. */
  private final World world;

  /**
   * Creates the surface bound to a world.
   *
   * @param world the Bukkit world
   */
  public BukkitBuoyancySurface(World world) {
    this.world = world;
  }

  @Override
  public boolean isWater(int x, int y, int z) {
    return world.getBlockAt(x, y, z).getType() == Material.WATER;
  }

  @Override
  public boolean isClear(int x, int y, int z) {
    Material type = world.getBlockAt(x, y, z).getType();
    return type.isAir() || type == Material.WATER;
  }
}
