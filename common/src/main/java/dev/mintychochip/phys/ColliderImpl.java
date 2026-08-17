package dev.mintychochip.phys;

import java.util.Objects;

/**
 * Immutable collider: shape, material, and body-local transform.
 *
 * @param shape geometry
 * @param material physical properties
 * @param localTransform offset from the body origin
 */
public record ColliderImpl(Shape shape, Material material, Transform localTransform)
    implements Collider {
  public ColliderImpl {
    Objects.requireNonNull(shape);
    Objects.requireNonNull(material);
    Objects.requireNonNull(localTransform);
  }
}
