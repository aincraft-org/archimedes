package dev.mintychochip.phys;

public interface Shape {
  Bounds bounds(Transform transform);

  double volume();
}
