package dev.mintychochip.phys;

import java.util.List;
import java.util.Objects;
import org.joml.Vector3d;

/**
 * Quadratic drag opposing linear velocity.
 *
 * <p>The one-arg form is lumped {@code F = −c |v| v}. The density form is {@code F = −c · ρ · |v|
 * v} with volume-weighted mean {@code ρ} over colliders (body position if none).
 */
public final class QuadraticDragForce implements Force {
  /** Drag coefficient {@code c}. */
  private final double coefficient;

  /** Optional medium density; {@code null} means lumped (no density factor). */
  private final DensityField medium;

  /**
   * @param coefficient non-negative quadratic coefficient {@code c}
   */
  public QuadraticDragForce(double coefficient) {
    this(coefficient, null);
  }

  /**
   * Density-scaled quadratic drag.
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   * @param medium density sampler
   */
  public QuadraticDragForce(double coefficient, DensityField medium) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
    this.medium = medium;
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
    double scale = coefficient * density(body) * speed;
    return new Result(velocity.mul(-scale), new Vector3d());
  }

  private double density(Body body) {
    if (medium == null) {
      return 1.0;
    }
    List<Collider> colliders = body.colliders();
    if (colliders.isEmpty()) {
      return medium.density(body.transform().position());
    }
    double mass = 0;
    double weighted = 0;
    Vector3d center = new Vector3d();
    for (Collider collider : colliders) {
      body.transform().position().add(collider.localTransform().position(), center);
      double volume = Math.max(collider.shape().volume(), 0);
      mass += volume;
      weighted += volume * medium.density(center);
    }
    if (mass == 0) {
      return medium.density(body.transform().position());
    }
    return weighted / mass;
  }
}
