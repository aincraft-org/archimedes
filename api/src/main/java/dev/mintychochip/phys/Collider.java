package dev.mintychochip.phys;

public interface Collider {
  Shape shape();
  Material material();
  Transform localTransform();
}
