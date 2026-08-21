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
   * @return buoyancy {@code F = −m g} at the displaced-mass centroid about the center of mass
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    DensityField field = medium != null ? medium : DensityField.liquid(world.fluidField());
    double displacedMass = 0;
    Vector3d moment = new Vector3d();
    for (Collider collider : body.colliders()) {
      DensitySampling.Displacement d = DensitySampling.displacement(body, collider, field);
      displacedMass += d.mass();
      if (d.mass() > 0) {
        moment.fma(d.mass(), d.centroid());
      }
    }
    Vector3d force = new Vector3d(world.gravity()).mul(-displacedMass);
    if (displacedMass == 0) {
      return new Result(force, new Vector3d());
    }
    Vector3d centroid = moment.div(displacedMass);
    Vector3d r = centroid.sub(MassProperties.worldCenterOfMass(body), new Vector3d());
    return new Result(force, r.cross(force, new Vector3d()));
  }
}
