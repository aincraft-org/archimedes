package dev.mintychochip.phys;

import org.joml.Vector3dc;

public interface FluidField {
  boolean isFluid(Vector3dc point);

  double density(Vector3dc point);
}
