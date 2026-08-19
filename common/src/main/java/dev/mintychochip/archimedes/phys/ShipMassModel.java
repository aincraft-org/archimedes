package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.Vehicle;

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
   * @param ship ship whose intact blocks are included
   * @param resolver material-key resolver
   * @param config density and player-mass configuration
   * @param riderCount number of players aboard
   * @return total positive mass
   * @throws IllegalArgumentException if {@code riderCount} is negative
   */
  public static double mass(
      Vehicle ship, MaterialKeyResolver resolver, ShipConfig config, int riderCount) {
    if (riderCount < 0) throw new IllegalArgumentException("negative rider count");
    double total = riderCount * config.playerMass();
    for (ShipBlock block : ship.intactBlocks()) {
      String key = resolver.key(block);
      total += densityOf(key, config);
    }
    return total;
  }

  /**
   * Resolves a block's mass density. Cloth without its own table entry uses the white-wool value so
   * sails stay light enough for a wooden deck to float.
   *
   * @param key resolved material key
   * @param config density table
   * @return positive density
   */
  private static double densityOf(String key, ShipConfig config) {
    Double density = config.materialDensities().get(key);
    if (density != null) {
      return density;
    }
    if (isCloth(key)) {
      Double cloth = config.materialDensities().get("minecraft:white_wool");
      if (cloth != null) {
        return cloth;
      }
    }
    return config.defaultMaterialDensity();
  }

  /**
   * @param key resolved material key
   * @return whether the key is wool or banner cloth
   */
  private static boolean isCloth(String key) {
    return key.endsWith("_wool") || key.endsWith("_banner") || key.endsWith("_wall_banner");
  }
}
