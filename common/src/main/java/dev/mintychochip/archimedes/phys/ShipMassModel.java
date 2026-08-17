package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;

public final class ShipMassModel {
  private ShipMassModel() {}

  public static double mass(Ship ship, MaterialKeyResolver resolver, ShipConfig config, int riderCount) {
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
