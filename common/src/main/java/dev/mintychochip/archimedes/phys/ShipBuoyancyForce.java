package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.MassProperties;
import dev.mintychochip.phys.World;
import org.joml.Vector3d;

/**
 * Vehicle-column hydrostatic lift: wet-cell volume × fluid density × |g|, upward only.
 *
 * <p>Displacement uses {@link WaterlineResolver#displacement} so a partially submerged deck
 * contributes only its wet fraction, applied at the wet-cell centroid. Weight is applied separately
 * by {@link GravityForce}.
 */
public final class ShipBuoyancyForce implements Force {
  /**
   * Computes upward buoyancy for the current waterline sample.
   *
   * @param body ship body to sample
   * @param world world supplying water, density, and gravity
   * @return vertical buoyancy at the center of buoyancy about the center of mass
   */
  @Override
  public Result apply(Body body, World world) {
    WaterlineResolver.Displacement d = WaterlineResolver.displacement(body, world);
    double gMag = Math.abs(world.gravity().y());
    Vector3d force = new Vector3d(0, d.mass() * gMag, 0);
    if (d.mass() == 0) {
      return new Result(force, new Vector3d());
    }
    Vector3d r = d.centroid().sub(MassProperties.worldCenterOfMass(body), new Vector3d());
    return new Result(force, r.cross(force, new Vector3d()));
  }
}
