package dev.mintychochip.phys;

/**
 * Physical material properties used by a {@link Collider}.
 *
 * @param density material density
 */
public record Material(double density) {
  /**
   * Creates a material with a finite, non-negative density.
   *
   * @param density material density
   * @throws IllegalArgumentException if density is non-finite or negative
   */
  public Material {
    if (!Double.isFinite(density) || density < 0) {
      throw new IllegalArgumentException("density must be finite and non-negative");
    }
  }
}
