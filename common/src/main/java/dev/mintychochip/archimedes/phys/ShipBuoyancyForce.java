package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.World;
import org.joml.Vector3d;

/**
 * Ship-column hydrostatic lift: number of submerged colliders × fluid density × |g|, upward only.
 *
 * <p>The buoyancy is based on the count reported by {@link WaterlineResolver#submergedVolume}, not
 * the exact volume of each collider. Weight is applied separately by {@link GravityForce}.
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
    double gMag = Math.abs(world.gravity().y());
    double displaced = WaterlineResolver.displacedMass(body, world);
    return new Result(new Vector3d(0, displaced * gMag, 0), new Vector3d());
  }
}
