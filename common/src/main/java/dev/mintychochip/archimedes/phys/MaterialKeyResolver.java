package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.ShipBlock;

public interface MaterialKeyResolver {
  String key(ShipBlock block);
}
