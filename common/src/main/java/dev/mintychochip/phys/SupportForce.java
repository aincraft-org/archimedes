package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/** Normal support equal to the plane's gravity-derived contact load. */
public final class SupportForce implements Force {
  /** Supporting plane. */
  private final ContactPlane plane;

  /**
   * Creates a support force for the supplied contact plane.
   *
   * @param plane plane providing contact and normal-load calculations
   */
  public SupportForce(ContactPlane plane) {
    this.plane = Objects.requireNonNull(plane);
  }

  /**
   * Applies the plane's compressive gravity load as a normal force.
   *
   * @param body body whose contact and mass are sampled
   * @param world world supplying gravity
   * @return normal support force, or zero when the body is not loaded by the plane
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    double load = plane.gravityLoad(body, world);
    if (load == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    return new Result(new Vector3d(plane.normal()).mul(load), new Vector3d());
  }
}
