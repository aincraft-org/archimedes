package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/** Quadratic drag opposing linear velocity: {@code F = −c |v| v}. */
public final class QuadraticDragForce implements Force {
  /** Drag coefficient {@code c}. */
  private final double coefficient;

  /**
   * @param coefficient non-negative quadratic coefficient {@code c}
   */
  public QuadraticDragForce(double coefficient) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
  }

  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    Vector3d velocity = new Vector3d(body.linearVelocity());
    double speed = velocity.length();
    if (speed == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    return new Result(velocity.mul(-coefficient * speed), new Vector3d());
  }
}
