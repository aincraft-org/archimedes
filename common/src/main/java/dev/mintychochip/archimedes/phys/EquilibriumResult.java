package dev.mintychochip.archimedes.phys;

public record EquilibriumResult(
    boolean equilibrium, double targetY, double residual, String reason) {
  public static EquilibriumResult none(String reason) {
    return new EquilibriumResult(false, 0, Double.NaN, reason);
  }
}
