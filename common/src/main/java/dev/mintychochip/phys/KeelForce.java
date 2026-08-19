package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Lateral resistance: quadratic drag of the beam-normal component of apparent velocity.
 *
 * <p>{@code s = v_app · n̂}, {@code F = −c ρ |s| s n̂}. Surge along the keel ({@code s = 0}) is
 * zero. {@code v_app = v − u_flow}.
 */
public final class KeelForce implements Force {
  /** Unit beam normal in the body frame. */
  private final Vector3d localNormal;

  /** Quadratic coefficient {@code c}. */
  private final double coefficient;

  /** Density sampler. */
  private final DensityField medium;

  /** Medium flow. */
  private final FlowField flow;

  /**
   * @param localNormal body-frame beam normal (normalized on store)
   * @param coefficient non-negative quadratic coefficient
   * @param medium density sampler
   * @param flow medium velocity
   */
  public KeelForce(Vector3dc localNormal, double coefficient, DensityField medium, FlowField flow) {
    Objects.requireNonNull(localNormal);
    Vectors.requireFinite(localNormal);
    if (localNormal.lengthSquared() == 0) {
      throw new IllegalArgumentException("normal must be non-zero");
    }
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.localNormal = new Vector3d(localNormal).normalize();
    this.coefficient = coefficient;
    this.medium = Objects.requireNonNull(medium);
    this.flow = Objects.requireNonNull(flow);
  }

  /**
   * Applies beam-normal quadratic resistance of apparent velocity.
   *
   * @param body body whose velocity and orientation are sampled
   * @param world world context; required for the force contract
   * @return lateral force and zero torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    Vector3d apparent = new Vector3d(body.linearVelocity());
    apparent.sub(flow.velocity(body.transform().position()));
    Vector3d normal = body.transform().orientation().transform(localNormal, new Vector3d());
    double slip = apparent.dot(normal);
    if (slip == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    double density = medium.density(body.transform().position());
    Vector3d force = normal.mul(-coefficient * density * Math.abs(slip) * slip);
    return new Result(force, new Vector3d());
  }
}
