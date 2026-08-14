package dev.jlo.ships.bukkit;

import dev.jlo.ships.deck.DeckSurface;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Bukkit-backed deck surface: deploys barrier support blocks only at cleared
 * positions and tracks exactly what this instance placed, so cleanup never
 * touches barriers owned by other ships or builds.
 */
public final class BukkitDeckSurface implements DeckSurface {
  private final World world;
  private final Set<String> placed = new HashSet<>();

  /** Creates the surface bound to a world. */
  public BukkitDeckSurface(World world) {
    this.world = world;
  }

  @Override
  public boolean canPlace(int x, int y, int z) {
    return true;
  }

  @Override
  public boolean isClear(int x, int y, int z) {
    return world.getBlockAt(x, y, z).getType().isAir();
  }

  @Override
  public boolean placeBarrier(int x, int y, int z) {
    if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
      return false;
    }
    world.getBlockAt(x, y, z).setType(Material.BARRIER, false);
    placed.add(key(x, y, z));
    return true;
  }

  @Override
  public void removeBarrier(int x, int y, int z) {
    if (placed.remove(key(x, y, z))) {
      world.getBlockAt(x, y, z).setType(Material.AIR, false);
    }
  }

  private static String key(int x, int y, int z) {
    return x + "," + y + "," + z;
  }
}