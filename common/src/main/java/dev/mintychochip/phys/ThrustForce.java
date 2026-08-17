package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Directed thrust of fixed magnitude along a body-local axis. */
public final class ThrustForce implements Force {
  /** Unit-ish direction in the body frame. */
  private final Vector3d localDirection;

  /** Thrust magnitude. */
  private final double magnitude;

  /**
   * @param localDirection body-frame thrust axis (normalized on store)
   * @param magnitude non-negative force magnitude
   */
  public ThrustForce(Vector3dc localDirection, double magnitude) {
    Objects.requireNonNull(localDirection);
    Vectors.requireFinite(localDirection);
    if (localDirection.lengthSquared() == 0) {
      throw new IllegalArgumentException("direction must be non-zero");
    }
    if (!Double.isFinite(magnitude) || magnitude < 0) {
      throw new IllegalArgumentException("magnitude must be finite and non-negative");
    }
    this.localDirection = new Vector3d(localDirection).normalize();
    this.magnitude = magnitude;
  }

  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    Vector3d worldDir =
        body.transform().orientation().transform(localDirection, new Vector3d()).mul(magnitude);
    return new Result(worldDir, new Vector3d());
  }
}
