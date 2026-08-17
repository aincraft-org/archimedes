package dev.mintychochip.phys;

public record Material(double density) {
  public Material {
    Vector3.finite(density);
    if (density < 0) throw new IllegalArgumentException("negative density");
  }
}
