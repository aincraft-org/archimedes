package dev.mintychochip.phys;

public record Material(double density) {
  public Material {
    if (!Double.isFinite(density) || density < 0)
      throw new IllegalArgumentException("density must be finite and non-negative");
  }
}
