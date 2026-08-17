package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.World;
import org.joml.Vector3d;

/**
 * Ship-column hydrostatic lift: displaced unit volume × fluid density × |g|, upward only.
 *
 * <p>Weight is applied separately by {@link dev.mintychochip.phys.GravityForce}.
 */
public final class ShipBuoyancyForce implements Force {
  /**
   * Computes upward buoyancy for the current waterline sample.
   *
   * @param body ship body to sample
   * @param world world supplying water, density, and gravity
   * @return vertical buoyancy with zero torque
   */
  @Override
  public Result apply(Body body, World world) {
    int submerged = WaterlineResolver.submergedVolume(body, world);
    double gMag = Math.abs(world.gravity().y());
    double density = world.fluidField().density(body.transform().position());
    return new Result(new Vector3d(0, submerged * density * gMag, 0), new Vector3d());
  }
}
