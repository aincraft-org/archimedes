package dev.mintychochip.phys;

import java.util.List;
import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Quadratic drag scaled by passable vegetation occupancy: {@code F = −c · σ · |v| v}.
 *
 * <p>{@code σ} is the mean {@link World#vegetation} at collider world centers, or at the body
 * position when the body has no colliders.
 */
public final class VegetationDragForce implements Force {
  /** Drag coefficient {@code c}. */
  private final double coefficient;

  /**
   * @param coefficient non-negative quadratic coefficient {@code c}
   */
  public VegetationDragForce(double coefficient) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
  }

  /**
   * Applies vegetation-scaled quadratic drag opposing linear velocity.
   *
   * @param body body whose velocity and colliders are sampled
   * @param world world supplying vegetation occupancy
   * @return drag force and zero torque
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
    double occupancy = occupancy(body, world);
    if (occupancy == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    return new Result(velocity.mul(-coefficient * occupancy * speed), new Vector3d());
  }

  private static double occupancy(Body body, World world) {
    List<Collider> colliders = body.colliders();
    if (colliders.isEmpty()) {
      return clamp(world.vegetation(body.transform().position()));
    }
    double sum = 0;
    Vector3d center = new Vector3d();
    for (Collider collider : colliders) {
      body.transform()
          .position()
          .add(collider.localTransform().position(), center);
      sum += world.vegetation(center);
    }
    return clamp(sum / colliders.size());
  }

  private static double clamp(double occupancy) {
    if (!Double.isFinite(occupancy) || occupancy <= 0) {
      return 0;
    }
    return Math.min(1.0, occupancy);
  }
}
