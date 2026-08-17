package dev.mintychochip.phys;

public interface World {
  Vector3 gravity();

  FluidField fluidField();

  double timeStep();

  default boolean isObstacle(Vector3 point) {
    return false;
  }
}
