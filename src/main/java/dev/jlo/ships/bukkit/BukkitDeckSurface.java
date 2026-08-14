package dev.jlo.ships.bukkit;

import dev.jlo.ships.deck.DeckSurface;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Bukkit-backed deck surface: deploys barrier support blocks only at cleared positions and tracks
 * exactly what this instance placed, so cleanup never touches barriers owned by other ships or
 * builds.
 */
public final class BukkitDeckSurface implements DeckSurface {
  /** The Bukkit world being modified. */
  private final World world;

  /** Positions this instance placed, tracked for ownership-safe cleanup. */
  private final Set<String> placed = new HashSet<>();

  /**
   * Creates the surface bound to a world.
   *
   * @param world the Bukkit world
   */
  public BukkitDeckSurface(World world) {
    this.world = world;
  }

  /**
   * Returns whether blocking is permitted in the position.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return true
   */
  @Override
  public boolean canPlace(int x, int y, int z) {
    return true;
  }

  /**
   * Returns whether the position is currently air.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return true when the position is air
   */
  @Override
  public boolean isClear(int x, int y, int z) {
    return world.getBlockAt(x, y, z).getType().isAir();
  }

  /**
   * Places a barrier support block, tracking it for later removal.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return false when the position is not air
   */
  @Override
  public boolean placeBarrier(int x, int y, int z) {
    if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
      return false;
    }
    world.getBlockAt(x, y, z).setType(Material.BARRIER, false);
    placed.add(key(x, y, z));
    return true;
  }

  /**
   * Removes a barrier this instance placed.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   */
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
