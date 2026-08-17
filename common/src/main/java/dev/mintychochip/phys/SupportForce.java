package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/** Normal support equal to the plane's gravity-derived contact load. */
public final class SupportForce implements Force {
  /** Supporting plane. */
  private final ContactPlane plane;

  public SupportForce(ContactPlane plane) {
    this.plane = Objects.requireNonNull(plane);
  }

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
