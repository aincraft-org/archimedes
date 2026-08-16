package dev.jlo.ships.command;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Resolves a non-air targeted block through {@link Player#getTargetBlockExact(int)}. */
public final class BukkitTargetResolver implements TargetResolver {
  /** Maximum targeting distance in blocks. */
  private final int distance;

  /**
   * Creates the resolver for a distance.
   *
   * @param distance the maximum targeting distance
   */
  public BukkitTargetResolver(int distance) {
    this.distance = distance;
  }

  /**
   * Resolves the targeted ship block.
   *
   * @param player the acting player
   * @return the resolved target, or null when nothing is in range
   */
  @Override
  public Target resolve(Player player) {
    Block block = player.getTargetBlockExact(distance);
    if (block == null || block.getType().isAir()) {
      return null;
    }
    return new Target(block.getX(), block.getY(), block.getZ(), block.getWorld().getUID());
  }
}
