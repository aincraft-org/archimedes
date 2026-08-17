package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.ShipBlock;

/** Maps a ship block to the canonical key used by configured material densities. */
public interface MaterialKeyResolver {
  /**
   * Resolves a block's material key.
   *
   * @param block block whose material is being classified
   * @return canonical configuration key
   */
  String key(ShipBlock block);
}
