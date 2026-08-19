package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/**
 * Quadratic drag opposing apparent velocity: {@code F = −c |v_app| v_app}, optionally times {@code
 * ρ}. Apparent velocity is {@code v − u_flow}; missing flow is still water/air ({@code u = 0}).
 */
public final class QuadraticDragForce implements Force {
  /** Drag coefficient {@code c}. */
  private final double coefficient;

  /** Null means lumped {@code F = −c |v_app| v_app}; non-null multiplies by sampled density. */
  private final DensityField medium;

  /** Null means still medium ({@code u_flow = 0}). */
  private final FlowField flow;

  /**
   * Lumped quadratic drag. Does not sample a {@link DensityField}. Apparent velocity is body {@code
   * v} (still medium).
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   */
  public QuadraticDragForce(double coefficient) {
    this(coefficient, null, null, false);
  }

  /**
   * Density-scaled quadratic drag: {@code F = −c ρ |v| v} in still medium.
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   * @param medium density sampler; required
   */
  public QuadraticDragForce(double coefficient, DensityField medium) {
    this(coefficient, Objects.requireNonNull(medium), null, true);
  }

  /**
   * Lumped quadratic drag against a flow: {@code F = −c |v − u| (v − u)}.
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   * @param flow medium velocity; required
   */
  public QuadraticDragForce(double coefficient, FlowField flow) {
    this(coefficient, null, Objects.requireNonNull(flow), false);
  }

  /**
   * Density-scaled relative-flow drag: {@code F = −c ρ |v_app| v_app}, {@code v_app = v − u_flow}.
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   * @param medium density sampler; required
   * @param flow medium velocity; required
   */
  public QuadraticDragForce(double coefficient, DensityField medium, FlowField flow) {
    this(coefficient, Objects.requireNonNull(medium), Objects.requireNonNull(flow), true);
  }

  private QuadraticDragForce(
      double coefficient, DensityField medium, FlowField flow, boolean requireMedium) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    if (requireMedium) {
      Objects.requireNonNull(medium);
    }
    this.coefficient = coefficient;
    this.medium = medium;
    this.flow = flow;
  }

  /**
   * @return whether this instance multiplies by a density field
   */
  public boolean densityScaled() {
    return medium != null;
  }

  /**
   * Applies drag opposing apparent velocity {@code v − u_flow} with magnitude quadratic in that
   * speed.
   *
   * @param body body whose linear velocity is sampled
   * @param world world context; required for the force contract
   * @return quadratic drag force and zero torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    Vector3d apparent = new Vector3d(body.linearVelocity());
    if (flow != null) {
      apparent.sub(flow.velocity(body.transform().position()));
    }
    double speed = apparent.length();
    if (speed == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    double scale = coefficient;
    if (medium != null) {
      scale *= DensitySampling.meanDensity(body, medium);
    }
    return new Result(apparent.mul(-scale * speed), new Vector3d());
  }
}
