package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/** Weight: mass times the world's gravity vector. */
public final class GravityForce implements Force {
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    return new Result(new Vector3d(world.gravity()).mul(body.mass()), new Vector3d());
  }
}
