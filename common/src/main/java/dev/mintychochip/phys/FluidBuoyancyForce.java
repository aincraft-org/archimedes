package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

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

  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    DensityField field = medium != null ? medium : DensityField.liquid(world.fluidField());
    double displacedMass = 0;
    for (Collider collider : body.colliders()) {
      displacedMass += displacedMass(body, collider, field);
    }
    return new Result(new Vector3d(world.gravity()).mul(-displacedMass), new Vector3d());
  }

  private static double displacedMass(Body body, Collider collider, DensityField field) {
    double volume = collider.shape().volume();
    if (volume <= 0) {
      return 0;
    }
    Vector3d worldCenter =
        body.transform().position().add(collider.localTransform().position(), new Vector3d());
    Bounds bounds =
        collider
            .shape()
            .bounds(new Transform(worldCenter, collider.localTransform().orientation()));
    Vector3dc min = bounds.min();
    Vector3dc max = bounds.max();
    double sx = max.x() - min.x();
    double sy = max.y() - min.y();
    double sz = max.z() - min.z();
    int nx = sampleCount(sx);
    int ny = sampleCount(sy);
    int nz = sampleCount(sz);
    double cellVolume = volume / (nx * ny * nz);
    double mass = 0;
    for (int ix = 0; ix < nx; ix++) {
      for (int iy = 0; iy < ny; iy++) {
        for (int iz = 0; iz < nz; iz++) {
          Vector3d sample =
              new Vector3d(
                  min.x() + (ix + 0.5) * (sx / nx),
                  min.y() + (iy + 0.5) * (sy / ny),
                  min.z() + (iz + 0.5) * (sz / nz));
          mass += field.density(sample) * cellVolume;
        }
      }
    }
    return mass;
  }

  private static int sampleCount(double extent) {
    if (extent <= 0) {
      return 1;
    }
    return Math.max(2, (int) Math.ceil(extent));
  }
}
