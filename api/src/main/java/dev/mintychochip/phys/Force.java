package dev.mintychochip.phys;

public interface Force {
  Result apply(Body body, World world);
  record Result(Vector3 force, Vector3 torque) {}
}
