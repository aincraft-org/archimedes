package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/**
 * Linear viscous drag opposing velocity: {@code F = −c v}. Distinct from {@link
 * QuadraticDragForce}.
 */
public final class ViscousDragForce implements Force {
  /** Linear coefficient {@code c}. */
  private final double coefficient;

  /**
   * @param coefficient non-negative linear coefficient {@code c}
   */
  public ViscousDragForce(double coefficient) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
  }

  /**
   * Applies linear drag opposing the body's velocity.
   *
   * @param body body whose linear velocity is sampled
   * @param world world context; required for the force contract
   * @return viscous drag force and zero torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    return new Result(new Vector3d(body.linearVelocity()).mul(-coefficient), new Vector3d());
  }
}
