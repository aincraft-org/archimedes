package dev.mintychochip.phys;

public interface Bounds {
  Vector3 min();
  Vector3 max();
  double volume();
  boolean contains(Vector3 point);
}
