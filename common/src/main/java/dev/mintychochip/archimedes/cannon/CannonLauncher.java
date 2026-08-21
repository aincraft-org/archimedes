package dev.mintychochip.archimedes.cannon;

import java.util.UUID;

/** Launches a platform-specific cannon projectile. */
@FunctionalInterface
public interface CannonLauncher {
  /**
   * Spawns the projectile for a resolved shot.
   *
   * @param shot world-space muzzle and direction
   */
  void launch(Shot shot);

  /**
   * Fully resolved cannon shot in world coordinates.
   *
   * @param shipId firing ship
   * @param shooterId firing player
   * @param x muzzle x
   * @param y muzzle y
   * @param z muzzle z
   * @param dx unit x direction
   * @param dy unit y direction
   * @param dz unit z direction
   */
  record Shot(
      UUID shipId, UUID shooterId, double x, double y, double z, double dx, double dy, double dz) {}
}
