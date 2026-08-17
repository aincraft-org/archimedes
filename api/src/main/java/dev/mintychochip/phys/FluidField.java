package dev.mintychochip.phys;

public interface FluidField {
  boolean isFluid(Vector3 point);

  double density(Vector3 point);
}
