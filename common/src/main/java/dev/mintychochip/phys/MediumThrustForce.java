package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Density-scaled thrust at a body-local point: {@code F = k ρ n̂}, {@code τ = r × F}. */
public final class MediumThrustForce implements Force {
  /** Application point in the body frame. */
  private final Vector3d localPoint;

  /** Unit thrust axis in the body frame. */
  private final Vector3d localAxis;

  /** Thrust coefficient {@code k}. */
  private final double coefficient;

  /** Null means world's liquid at apply time. */
  private final DensityField medium;

  /**
   * World-liquid actuator. Samples {@link DensityField#liquid(FluidField)} at the application
   * point.
   *
   * @param localPoint body-frame application point
   * @param localAxis body-frame thrust axis (normalized on store)
   * @param coefficient non-negative {@code k}
   */
  public MediumThrustForce(Vector3dc localPoint, Vector3dc localAxis, double coefficient) {
    this.localPoint = copyPoint(localPoint);
    this.localAxis = copyAxis(localAxis);
    this.coefficient = requireCoefficient(coefficient);
    this.medium = null;
  }

  /**
   * Actuator that samples an explicit medium at the application point.
   *
   * @param localPoint body-frame application point
   * @param localAxis body-frame thrust axis (normalized on store)
   * @param coefficient non-negative {@code k}
   * @param medium density sampler
   */
  public MediumThrustForce(
      Vector3dc localPoint, Vector3dc localAxis, double coefficient, DensityField medium) {
    this.localPoint = copyPoint(localPoint);
    this.localAxis = copyAxis(localAxis);
    this.coefficient = requireCoefficient(coefficient);
    this.medium = Objects.requireNonNull(medium);
  }

  /**
   * Applies density-scaled thrust at the rotated application point.
   *
   * @param body body whose pose is sampled
   * @param world world supplying the default liquid field when no medium was given
   * @return force along the world axis and {@code r × F} torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    DensityField field = medium != null ? medium : DensityField.liquid(world.fluidField());
    Vector3d worldPoint = body.transform().orientation().transform(localPoint, new Vector3d());
    worldPoint.add(body.transform().position());
    double density = field.density(worldPoint);
    Vector3d force =
        body.transform()
            .orientation()
            .transform(localAxis, new Vector3d())
            .mul(coefficient * density);
    Vector3d radius = new Vector3d(worldPoint).sub(body.transform().position());
    return new Result(force, radius.cross(force, new Vector3d()));
  }

  private static Vector3d copyPoint(Vector3dc localPoint) {
    Objects.requireNonNull(localPoint);
    Vectors.requireFinite(localPoint);
    return new Vector3d(localPoint);
  }

  private static Vector3d copyAxis(Vector3dc localAxis) {
    Objects.requireNonNull(localAxis);
    Vectors.requireFinite(localAxis);
    if (localAxis.lengthSquared() == 0) {
      throw new IllegalArgumentException("axis must be non-zero");
    }
    return new Vector3d(localAxis).normalize();
  }

  private static double requireCoefficient(double coefficient) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    return coefficient;
  }
}
