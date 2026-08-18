package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.World;
import org.joml.Vector3d;

/**
 * Ship-column hydrostatic lift: wet-cell volume × fluid density × |g|, upward only.
 *
 * <p>Displacement uses {@link WaterlineResolver#displacedMass} so a partially submerged deck
 * contributes only its wet fraction. Weight is applied separately by {@link GravityForce}.
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
