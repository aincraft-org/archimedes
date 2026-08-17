package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Aerodynamic lift along a body-local axis. Magnitude is {@code c · airspeed²} where airspeed is
 * {@code |v × n|}, so lift is zero at rest and when motion is only along the lift axis.
 */
public final class LiftForce implements Force {
  /** Lift direction in the body frame. */
  private final Vector3d localLift;

  /** Lumped lift coefficient. */
  private final double coefficient;

  /**
   * @param localLift body-frame lift axis (normalized on store)
   * @param coefficient lumped {@code ½ ρ S C_L} coefficient
   */
  public LiftForce(Vector3dc localLift, double coefficient) {
    Objects.requireNonNull(localLift);
    Vectors.requireFinite(localLift);
    if (localLift.lengthSquared() == 0) {
      throw new IllegalArgumentException("lift axis must be non-zero");
    }
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.localLift = new Vector3d(localLift).normalize();
    this.coefficient = coefficient;
  }

  /**
   * Computes lift along the world-space lift axis from velocity transverse to that axis.
   *
   * @param body body whose orientation and linear velocity are sampled
   * @param world world context; required for the force contract
   * @return lift force and zero torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    Vector3d liftDir = body.transform().orientation().transform(localLift, new Vector3d());
    Vector3d velocity = new Vector3d(body.linearVelocity());
    double airspeed = velocity.cross(liftDir, new Vector3d()).length();
    double lift = coefficient * airspeed * airspeed;
    return new Result(liftDir.mul(lift), new Vector3d());
  }
}
