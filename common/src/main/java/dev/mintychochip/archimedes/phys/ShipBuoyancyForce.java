package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.World;
import org.joml.Vector3d;

public final class ShipBuoyancyForce implements Force {
  @Override
  public Result apply(Body body, World world) {
    int submerged = WaterlineResolver.submergedVolume(body, world);
    double gMag = Math.abs(world.gravity().y());
    double buoyancy = submerged * world.fluidField().density(body.transform().position()) * gMag;
    double weight = body.mass() * gMag;
    double net = buoyancy - weight;
    return new Result(new Vector3d(0, net, 0), new Vector3d());
  }
}
