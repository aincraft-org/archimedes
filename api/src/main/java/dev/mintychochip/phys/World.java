package dev.mintychochip.phys;

import org.joml.Vector3dc;

public interface World {
  Vector3dc gravity();

  FluidField fluidField();

  double timeStep();

  default boolean isObstacle(Vector3dc point) {
    return false;
  }
}
