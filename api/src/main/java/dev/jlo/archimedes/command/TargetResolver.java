package dev.jlo.archimedes.command;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Resolves the block a player is looking at for assembly. Separated so the command's targeting
 * contract is unit-testable without a live player.
 */
@FunctionalInterface
public interface TargetResolver {
  /**
   * Resolves the targeted block.
   *
   * @param player the acting player
   * @return the resolved target, or null when no non-air target is in range
   */
  Target resolve(Player player);

  /**
   * A non-air target with world coordinates.
   *
   * @param x the target x coordinate
   * @param y the target y coordinate
   * @param z the target z coordinate
   * @param worldId the target world identifier
   */
  record Target(int x, int y, int z, UUID worldId) {}
}
