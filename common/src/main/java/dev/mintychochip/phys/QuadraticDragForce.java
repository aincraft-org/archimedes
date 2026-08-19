package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/** Quadratic drag opposing linear velocity: {@code F = −c |v| v}, optionally times {@code ρ}. */
public final class QuadraticDragForce implements Force {
  /** Drag coefficient {@code c}. */
  private final double coefficient;

  /** Null means lumped {@code F = −c |v| v}; non-null multiplies by sampled density. */
  private final DensityField medium;

  /**
   * Lumped quadratic drag. Does not sample a {@link DensityField}.
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   */
  public QuadraticDragForce(double coefficient) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
    this.medium = null;
  }

  /**
   * Density-scaled quadratic drag: {@code F = −c ρ |v| v}.
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   * @param medium density sampler; required
   */
  public QuadraticDragForce(double coefficient, DensityField medium) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
    this.medium = Objects.requireNonNull(medium);
  }

  /**
   * @return whether this instance multiplies by a density field
   */
  public boolean densityScaled() {
    return medium != null;
  }

  /**
   * Applies drag opposing linear velocity with magnitude quadratic in speed.
   *
   * @param body body whose linear velocity is sampled
   * @param world world context; required for the force contract
   * @return quadratic drag force and zero torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    Vector3d velocity = new Vector3d(body.linearVelocity());
    double speed = velocity.length();
    if (speed == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    double scale = coefficient;
    if (medium != null) {
      scale *= DensitySampling.meanDensity(body, medium);
    }
    return new Result(velocity.mul(-scale * speed), new Vector3d());
  }
}
