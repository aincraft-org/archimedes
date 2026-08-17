package dev.mintychochip.archimedes.phys.bukkit;

import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.phys.MaterialKeyResolver;

/** Resolves a Bukkit block's material key from its serialized block data. */
public final class BukkitMaterialKeyResolver implements MaterialKeyResolver {
  /**
   * Returns the material name without optional block-state properties.
   *
   * @param block block whose Bukkit block-data string is resolved
   * @return the portion before the first {@code '['}, or the complete data when absent
   */
  @Override
  public String key(ShipBlock block) {
    String data = block.blockData();
    int bracket = data.indexOf('[');
    return bracket == -1 ? data : data.substring(0, bracket);
  }
}
