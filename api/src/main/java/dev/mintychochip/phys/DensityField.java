package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3dc;

/**
 * Pointwise mass density of a fluid medium. Independent of {@link FluidField#isFluid}, so
 * atmosphere can be sampled without treating empty air as ship water.
 */
@FunctionalInterface
public interface DensityField {
  /**
   * Mass density at {@code point} in kg/m³.
   *
   * @param point world-space sample
   * @return finite non-negative density
   */
  double density(Vector3dc point);

  /**
   * Constant density everywhere, including empty air.
   *
   * @param density kg/m³
   * @return a field that ignores {@link FluidField#isFluid}
   */
  static DensityField uniform(double density) {
    if (!Double.isFinite(density) || density < 0) {
      throw new IllegalArgumentException("density must be finite and non-negative");
    }
    return point -> density;
  }

  /**
   * Liquid density only. Returns 0 wherever {@link FluidField#isFluid} is false, even if {@link
   * FluidField#density} is non-zero.
   *
   * @param fluids ship-submerging liquid field
   * @return a liquid-only sampler
   */
  static DensityField liquid(FluidField fluids) {
    Objects.requireNonNull(fluids);
    return point -> fluids.isFluid(point) ? fluids.density(point) : 0.0;
  }
}
