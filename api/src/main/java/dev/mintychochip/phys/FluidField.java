package dev.mintychochip.phys;

import org.joml.Vector3dc;

/** Samples whether positions are in liquid and the liquid density there. */
public interface FluidField {
  /**
   * Reports whether a world-space point lies in fluid.
   *
   * @param point point to test
   * @return whether the point lies in fluid
   */
  boolean isFluid(Vector3dc point);

  /**
   * Returns fluid density at a world-space point.
   *
   * @param point point to sample
   * @return fluid density
   */
  double density(Vector3dc point);
}
