package dev.mintychochip.phys;

/** Combines a shape, material, and body-local transform. */
public interface Collider {
  /**
   * Returns the geometric shape used by this collider.
   *
   * @return collider shape
   */
  Shape shape();

  /**
   * Returns the physical material used by this collider.
   *
   * @return collider material
   */
  Material material();

  /**
   * Returns the shape transform relative to its owning body.
   *
   * @return local transform
   */
  Transform localTransform();
}
