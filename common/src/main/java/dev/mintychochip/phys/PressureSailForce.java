package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * One-sided pressure sail: {@code F = q A max(n̂ · v̂_app, 0)² n̂} with {@code τ = r × F}.
 *
 * <p>Apparent wind is {@code v_wind(p) − v − ω × r}. Air density comes from a {@link DensityField};
 * flow comes from a {@link FlowField}. {@link FluidField#isFluid} is never read.
 */
public final class PressureSailForce implements Force {
  /** Center of pressure in the body frame. */
  private final Vector3d localPoint;

  /** Unit cloth normal in the body frame. */
  private final Vector3d localNormal;

  /** Sail area. */
  private final double area;

  /** Air density sampler. */
  private final DensityField medium;

  /** Wind / flow sampler. */
  private final FlowField wind;

  /**
   * @param localPoint body-frame center of pressure
   * @param localNormal body-frame cloth normal (normalized on store)
   * @param area positive sail area
   * @param medium air density
   * @param wind flow field
   */
  public PressureSailForce(
      Vector3dc localPoint,
      Vector3dc localNormal,
      double area,
      DensityField medium,
      FlowField wind) {
    Objects.requireNonNull(localPoint);
    Objects.requireNonNull(localNormal);
    Vectors.requireFinite(localPoint);
    Vectors.requireFinite(localNormal);
    if (localNormal.lengthSquared() == 0) {
      throw new IllegalArgumentException("normal must be non-zero");
    }
    if (!Double.isFinite(area) || area <= 0) {
      throw new IllegalArgumentException("area must be finite and positive");
    }
    this.localPoint = new Vector3d(localPoint);
    this.localNormal = new Vector3d(localNormal).normalize();
    this.area = area;
    this.medium = Objects.requireNonNull(medium);
    this.wind = Objects.requireNonNull(wind);
  }

  /**
   * @return body-frame unit cloth normal
   */
  public Vector3dc localNormal() {
    return localNormal;
  }

  /**
   * @return cloth area in square metres
   */
  public double area() {
    return area;
  }

  /**
   * Applies one-sided pressure from apparent wind on the cloth normal.
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
    Vector3d normal = body.transform().orientation().transform(localNormal, new Vector3d());
    double facing = apparent.dot(normal) / speed;
    if (facing <= 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    double density = medium.density(point);
    double dynamicPressure = 0.5 * density * speed * speed;
    Vector3d force = normal.mul(dynamicPressure * area * facing * facing);
    return new Result(force, radius.cross(force, new Vector3d()));
  }
}
