package dev.mintychochip.phys;

import org.joml.Vector3dc;

/** Environmental inputs consumed while integrating physics bodies. */
public interface World {
  /**
   * Returns the world gravity vector.
   *
   * @return gravity vector
   */
  Vector3dc gravity();

  /**
   * Returns the fluid field used for environmental sampling.
   *
   * @return fluid field
   */
  FluidField fluidField();

  /**
   * Returns the integration timestep in seconds.
   *
   * @return timestep in seconds
   */
  double timeStep();

  /**
   * Reports whether a point is occupied by an obstacle.
   *
   * @param point world-space point to test
   * @return {@code false} by default when no obstacle map is supplied
   */
  default boolean isObstacle(Vector3dc point) {
    return false;
  }
}
