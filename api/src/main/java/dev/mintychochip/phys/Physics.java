package dev.mintychochip.phys;

import java.util.Collection;

/** Stateless physics integrator operating on caller-owned bodies. */
public interface Physics {
  /**
   * Advances each supplied active body by one world timestep.
   *
   * @param world environmental inputs and timestep
   * @param bodies bodies to integrate; the implementation does not retain this collection
   */
  void step(World world, Collection<Body> bodies);
}
