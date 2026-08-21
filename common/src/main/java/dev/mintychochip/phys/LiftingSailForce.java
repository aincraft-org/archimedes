package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Angle-of-attack sail: lift perpendicular to apparent wind plus drag along it.
 *
 * <p>{@code v_app = v_wind(p) − v − ω × r}. {@code α} is the signed angle from the chord to the
 * incoming flow {@code −v_app} in the sail plane. {@code C_L = clamp(2α, ±1.2)}, {@code C_D = 0.08
 * + 0.1 C_L²}. {@link FluidField#isFluid} is never read.
 */
public final class LiftingSailForce implements Force {
  /** Lift slope {@code dC_L/dα} in the linear range. */
  private static final double LIFT_SLOPE = 2.0;

  /** Absolute cap on {@code C_L}. */
  private static final double MAX_CL = 1.2;

  /** Parasite drag coefficient. */
  private static final double CD0 = 0.08;

  /** Induced-drag factor in {@code C_D = C_{D0} + k C_L²}. */
  private static final double INDUCED = 0.1;

  /** Center of pressure in the body frame. */
  private final Vector3d localPoint;

  /** Unit chord (bow-ward along the boom) in the body frame. */
  private final Vector3d localChord;

  /** Unit span (mast) in the body frame. */
  private final Vector3d localSpan;

  /** Sail area. */
  private final double area;

  /** Air density. */
  private final DensityField medium;

  /** Wind. */
  private final FlowField wind;

  /**
   * @param localPoint body-frame center of pressure
   * @param localChord body-frame chord (normalized on store)
   * @param localSpan body-frame span (normalized on store)
   * @param area positive area
   * @param medium air density
   * @param wind flow field
   */
  public LiftingSailForce(
      Vector3dc localPoint,
      Vector3dc localChord,
      Vector3dc localSpan,
      double area,
      DensityField medium,
      FlowField wind) {
    Objects.requireNonNull(localPoint);
    Objects.requireNonNull(localChord);
    Objects.requireNonNull(localSpan);
    Vectors.requireFinite(localPoint);
    Vectors.requireFinite(localChord);
    Vectors.requireFinite(localSpan);
    if (localChord.lengthSquared() == 0) {
      throw new IllegalArgumentException("chord must be non-zero");
    }
    if (localSpan.lengthSquared() == 0) {
      throw new IllegalArgumentException("span must be non-zero");
    }
    if (!Double.isFinite(area) || area <= 0) {
      throw new IllegalArgumentException("area must be finite and positive");
    }
    this.localPoint = new Vector3d(localPoint);
    this.localChord = new Vector3d(localChord).normalize();
    this.localSpan = new Vector3d(localSpan).normalize();
    this.area = area;
    this.medium = Objects.requireNonNull(medium);
    this.wind = Objects.requireNonNull(wind);
  }

  /**
   * Applies lift and drag from apparent wind at the sail.
   *
   * @param body body whose pose and velocities are sampled
   * @param world world context; required for the force contract
   * @return sail force and {@code r × F} torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    Vector3d radius = MassProperties.radiusAboutCom(body, localPoint);
    Vector3d point = MassProperties.worldPoint(body, localPoint);
    Vector3d apparent =
        new Vector3d(wind.velocity(point))
            .sub(body.linearVelocity())
            .sub(new Vector3d(body.angularVelocity()).cross(radius));
    double speed = apparent.length();
    if (speed == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    double density = medium.density(point);
    if (density == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    Vector3d chord = body.transform().orientation().transform(localChord, new Vector3d());
    Vector3d span = body.transform().orientation().transform(localSpan, new Vector3d());
    Vector3d normal = chord.cross(span, new Vector3d());
    if (normal.lengthSquared() == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    normal.normalize();
    Vector3d incoming = new Vector3d(apparent).negate();
    double sin = incoming.dot(normal);
    double cos = incoming.dot(chord);
    double alpha = Math.atan2(sin, cos);
    double cl = Math.max(-MAX_CL, Math.min(MAX_CL, LIFT_SLOPE * alpha));
    double cd = CD0 + INDUCED * cl * cl;
    double q = 0.5 * density * speed * speed;
    Vector3d flowHat = new Vector3d(apparent).div(speed);
    Vector3d liftDir = flowHat.cross(span, new Vector3d());
    if (liftDir.lengthSquared() == 0) {
      liftDir.zero();
    } else {
      liftDir.normalize();
      if (cl < 0) {
        liftDir.negate();
        cl = -cl;
      }
    }
    Vector3d force = new Vector3d();
    force.fma(cl * q * area, liftDir);
    force.fma(cd * q * area, flowHat);
    return new Result(force, radius.cross(force, new Vector3d()));
  }
}
