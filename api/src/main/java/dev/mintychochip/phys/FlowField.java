package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Pointwise flow velocity. Independent of {@link FluidField#isFluid} and {@link DensityField}, so
 * several winds can be composed without treating air as ship water.
 */
@FunctionalInterface
public interface FlowField {
  /**
   * Flow velocity at {@code point} in m/s.
   *
   * @param point world-space sample
   * @return a finite velocity vector; each call returns a distinct instance
   */
  Vector3dc velocity(Vector3dc point);

  /**
   * No flow anywhere.
   *
   * @return a field whose velocity is the zero vector
   */
  static FlowField still() {
    return point -> new Vector3d();
  }

  /**
   * The same velocity at every point.
   *
   * @param velocity flow velocity
   * @return a uniform field
   */
  static FlowField uniform(Vector3dc velocity) {
    Objects.requireNonNull(velocity);
    Vectors.requireFinite(velocity);
    Vector3d stored = new Vector3d(velocity);
    return point -> new Vector3d(stored);
  }

  /**
   * Uniform velocity inside an axis-aligned box; still outside. Bounds are inclusive.
   *
   * @param min inclusive lower corner
   * @param max inclusive upper corner
   * @param velocity flow inside the box
   * @return a spatially limited field
   */
  static FlowField box(Vector3dc min, Vector3dc max, Vector3dc velocity) {
    Objects.requireNonNull(min);
    Objects.requireNonNull(max);
    Objects.requireNonNull(velocity);
    Vectors.requireFinite(min);
    Vectors.requireFinite(max);
    Vectors.requireFinite(velocity);
    if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
      throw new IllegalArgumentException("box min must be <= max on every axis");
    }
    Vector3d lo = new Vector3d(min);
    Vector3d hi = new Vector3d(max);
    Vector3d stored = new Vector3d(velocity);
    return point -> {
      Objects.requireNonNull(point);
      if (point.x() < lo.x()
          || point.x() > hi.x()
          || point.y() < lo.y()
          || point.y() > hi.y()
          || point.z() < lo.z()
          || point.z() > hi.z()) {
        return new Vector3d();
      }
      return new Vector3d(stored);
    };
  }

  /**
   * Sum of independent fields at each point. Empty compose is {@link #still()}.
   *
   * @param fields fields to add; each must be non-null
   * @return the superimposed flow
   */
  static FlowField compose(FlowField... fields) {
    Objects.requireNonNull(fields);
    FlowField[] copy = new FlowField[fields.length];
    for (int i = 0; i < fields.length; i++) {
      copy[i] = Objects.requireNonNull(fields[i]);
    }
    return point -> {
      Vector3d sum = new Vector3d();
      for (FlowField field : copy) {
        sum.add(field.velocity(point));
      }
      return sum;
    };
  }
}
