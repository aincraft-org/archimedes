package dev.mintychochip.phys;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Volume-grid density samples shared by hydrostatic buoyancy and density-scaled drag. */
final class DensitySampling {
  /** Prevents instantiation. */
  private DensitySampling() {}

  /**
   * Displaced fluid mass of one collider under {@code field}.
   *
   * @param body body providing the world translation
   * @param collider collider whose volume is sampled
   * @param field density sampler
   * @return finite non-negative mass
   */
  static double displacedMass(Body body, Collider collider, DensityField field) {
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

  /**
   * Volume-weighted mean density. Bodies with no positive volume sample the body position.
   *
   * @param body body whose colliders are sampled
   * @param field density sampler
   * @return mean density
   */
  static double meanDensity(Body body, DensityField field) {
    double mass = 0;
    double volume = 0;
    for (Collider collider : body.colliders()) {
      double part = collider.shape().volume();
      if (part <= 0) {
        continue;
      }
      mass += displacedMass(body, collider, field);
      volume += part;
    }
    if (volume <= 0) {
      return field.density(body.transform().position());
    }
    return mass / volume;
  }

  private static int sampleCount(double extent) {
    if (extent <= 0) {
      return 1;
    }
    return Math.max(2, (int) Math.ceil(extent));
  }
}
