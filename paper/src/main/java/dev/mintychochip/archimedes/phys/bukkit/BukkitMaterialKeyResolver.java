package dev.mintychochip.archimedes.phys.bukkit;

import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.phys.MaterialKeyResolver;

public final class BukkitMaterialKeyResolver implements MaterialKeyResolver {
  @Override
  public String key(ShipBlock block) {
    String data = block.blockData();
    int bracket = data.indexOf('[');
    return bracket == -1 ? data : data.substring(0, bracket);
  }
}
