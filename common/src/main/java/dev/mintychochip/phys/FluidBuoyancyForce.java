package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/**
 * Hydrostatic buoyancy: {@code F = −ρ_displaced V g}. The medium is either an explicit {@link
 * DensityField} or the world's liquid ({@link FluidField#isFluid} gated).
 */
public final class FluidBuoyancyForce implements Force {
  /** Null means sample {@link World#fluidField()} as liquid only. */
  private final DensityField medium;

  /** World's liquid only ({@link FluidField#isFluid} gated). */
  public FluidBuoyancyForce() {
    this.medium = null;
  }

  /**
   * Explicit medium (atmosphere, a custom liquid, etc.).
   *
   * @param medium density sampler used at every collider sample
   */
  public FluidBuoyancyForce(DensityField medium) {
    this.medium = Objects.requireNonNull(medium);
  }

  /**
   * Applies buoyancy from the configured medium or the world's liquid field.
   *
   * @param body body whose collider volumes are sampled
   * @param world world supplying gravity and, when needed, liquid density
   * @return upward buoyancy force proportional to displaced fluid mass
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    DensityField field = medium != null ? medium : DensityField.liquid(world.fluidField());
    double displacedMass = 0;
    for (Collider collider : body.colliders()) {
      displacedMass += DensitySampling.displacedMass(body, collider, field);
    }
    return new Result(new Vector3d(world.gravity()).mul(-displacedMass), new Vector3d());
  }
}
