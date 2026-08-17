package dev.mintychochip.phys;

import java.util.Collection;

public interface Physics {
  void step(World world, Collection<Body> bodies);
}
