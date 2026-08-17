package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Coulomb friction on a {@link ContactPlane}. Kinetic opposes tangent slip with {@code μ_k N};
 * static cancels sibling tangent load when {@code |T| ≤ μ_s N} at rest. {@code N = 0} off the
 * plane.
 */
public final class CoulombFrictionForce implements Force {
  /** Speed at or below which the body is treated as at rest. */
  private static final double REST_SPEED = 1e-6;

  /** Supporting plane supplying the contact normal and gravity load. */
  private final ContactPlane plane;

  /** Static coefficient {@code μ_s}. */
  private final double staticMu;

  /** Kinetic coefficient {@code μ_k}. */
  private final double kineticMu;

  /**
   * @param plane contact plane
   * @param staticMu static coefficient, {@code ≥ kineticMu}
   * @param kineticMu kinetic coefficient, non-negative
   */
  public CoulombFrictionForce(ContactPlane plane, double staticMu, double kineticMu) {
    this.plane = Objects.requireNonNull(plane);
    if (!Double.isFinite(staticMu) || staticMu < 0) {
      throw new IllegalArgumentException("staticMu must be finite and non-negative");
    }
    if (!Double.isFinite(kineticMu) || kineticMu < 0) {
      throw new IllegalArgumentException("kineticMu must be finite and non-negative");
    }
    if (staticMu < kineticMu) {
      throw new IllegalArgumentException("staticMu must be at least kineticMu");
    }
    this.staticMu = staticMu;
    this.kineticMu = kineticMu;
  }

  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    double load = plane.gravityLoad(body, world);
    if (load == 0) {
      return zero();
    }
    Vector3dc n = plane.normal();
    Vector3d tangentVelocity = tangent(body.linearVelocity(), n);
    if (tangentVelocity.length() > REST_SPEED) {
      return force(tangentVelocity.normalize().mul(-kineticMu * load));
    }
    Vector3d tangentLoad = siblingTangentLoad(body, world, n);
    double magnitude = tangentLoad.length();
    if (magnitude <= staticMu * load) {
      return force(tangentLoad.negate());
    }
    if (magnitude == 0) {
      return zero();
    }
    return force(tangentLoad.normalize().mul(-kineticMu * load));
  }

  private Vector3d siblingTangentLoad(Body body, World world, Vector3dc n) {
    Vector3d total = new Vector3d();
    for (Force force : body.forces()) {
      if (force == this || force instanceof CoulombFrictionForce) {
        continue;
      }
      total.add(force.apply(body, world).force());
    }
    return tangent(total, n);
  }

  private static Vector3d tangent(Vector3dc vector, Vector3dc n) {
    Vector3d v = new Vector3d(vector);
    return v.sub(new Vector3d(n).mul(v.dot(n)));
  }

  private static Result force(Vector3d linear) {
    return new Result(linear, new Vector3d());
  }

  private static Result zero() {
    return new Result(new Vector3d(), new Vector3d());
  }
}
