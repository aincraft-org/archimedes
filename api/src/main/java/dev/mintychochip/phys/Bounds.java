package dev.mintychochip.phys;

import org.joml.Vector3dc;

public interface Bounds {
  Vector3dc min();

  Vector3dc max();

  double volume();

  boolean contains(Vector3dc point);
}
