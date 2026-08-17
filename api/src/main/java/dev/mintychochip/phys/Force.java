package dev.mintychochip.phys;

import org.joml.Vector3dc;

public interface Force {
  Result apply(Body body, World world);

  record Result(Vector3dc force, Vector3dc torque) {}
}
