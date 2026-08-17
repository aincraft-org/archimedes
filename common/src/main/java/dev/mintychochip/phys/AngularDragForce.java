package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/** Angular viscous drag opposing spin: {@code τ = −c ω}. */
public final class AngularDragForce implements Force {
  /** Angular coefficient {@code c}. */
  private final double coefficient;

  /**
   * @param coefficient non-negative angular coefficient {@code c}
   */
  public AngularDragForce(double coefficient) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
  }

  /**
   * Applies angular drag opposing the body's angular velocity.
   *
   * @param body body whose angular velocity is sampled
   * @param world world context; required for the force contract
   * @return zero linear force and viscous angular torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    return new Result(new Vector3d(), new Vector3d(body.angularVelocity()).mul(-coefficient));
  }
}
