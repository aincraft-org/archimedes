package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** World-frame mass-property helpers for a rigid {@link Body}. */
public final class MassProperties {
  /** Prevents instantiation. */
  private MassProperties() {}

  /**
   * Transforms a body-local point into world coordinates.
   *
   * @param body rigid body providing the world pose
   * @param localPoint point in the body frame
   * @return world-space position of {@code localPoint}
   * @throws NullPointerException if either argument is {@code null}
   */
  public static Vector3d worldPoint(Body body, Vector3dc localPoint) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(localPoint);
    Vector3d p = body.transform().orientation().transform(localPoint, new Vector3d());
    return p.add(body.transform().position());
  }

  /**
   * World-space lever arm from the body's center of mass to a body-local point.
   *
   * @param body rigid body providing the center of mass and orientation
   * @param localPoint point in the body frame
   * @return {@code R (localPoint - com)}
   * @throws NullPointerException if either argument is {@code null}
   */
  public static Vector3d radiusAboutCom(Body body, Vector3dc localPoint) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(localPoint);
    Vector3d r = new Vector3d(localPoint).sub(body.centerOfMassLocal());
    return body.transform().orientation().transform(r, new Vector3d());
  }

  /**
   * World-space location of the body's center of mass.
   *
   * @param body rigid body
   * @return world-space center of mass
   * @throws NullPointerException if {@code body} is {@code null}
   */
  public static Vector3d worldCenterOfMass(Body body) {
    Objects.requireNonNull(body);
    return worldPoint(body, body.centerOfMassLocal());
  }
}
