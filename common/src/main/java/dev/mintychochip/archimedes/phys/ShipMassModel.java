package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;

/**
 * Computes ship mass from block material densities and rider mass.
 *
 * <p>Each block contributes its configured density, or the default density when its key is absent.
 */
public final class ShipMassModel {
  private ShipMassModel() {}

  /**
   * Sums material and player mass.
   *
   * @param ship ship whose blocks are included
   * @param resolver material-key resolver
   * @param config density and player-mass configuration
   * @param riderCount number of players aboard
   * @return total positive mass
   * @throws IllegalArgumentException if {@code riderCount} is negative
   */
  public static double mass(
      Ship ship, MaterialKeyResolver resolver, ShipConfig config, int riderCount) {
    if (riderCount < 0) throw new IllegalArgumentException("negative rider count");
    double total = riderCount * config.playerMass();
    for (ShipBlock block : ship.blocks()) {
      String key = resolver.key(block);
      Double density = config.materialDensities().get(key);
      total += density != null ? density : config.defaultMaterialDensity();
    }
    return total;
  }
}
