package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/** Weight: mass times the world's gravity vector. */
public final class GravityForce implements Force {
  /**
   * Computes downward or upward weight from the world's gravity vector.
   *
   * @param body body whose mass is used
   * @param world world supplying gravity
   * @return weight force and zero torque
   * @throws NullPointerException if either argument is {@code null}
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    return new Result(new Vector3d(world.gravity()).mul(body.mass()), new Vector3d());
  }
}
