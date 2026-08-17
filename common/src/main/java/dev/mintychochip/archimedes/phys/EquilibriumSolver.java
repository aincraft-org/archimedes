package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.Vector3;
import dev.mintychochip.phys.World;

public final class EquilibriumSolver {
  public EquilibriumResult solve(Body body, World world, ShipConfig config) {
    double g = Math.abs(world.gravity().y());
    if (g == 0) return EquilibriumResult.none("no gravity");
    double targetMass = body.mass();
    Transform original = body.transform();
    double originY = original.position().y();
    double bestY = originY;
    double bestError = Double.MAX_VALUE;
    double low = originY - config.maxFall();
    double high = originY + config.maxRise();
    try {
      for (double y = low; y <= high; y += 1.0) {
        body.setTransform(
            new Transform(
                new Vector3(original.position().x(), y, original.position().z()),
                original.orientation()));
        int submerged = WaterlineResolver.submergedVolume(body, world);
        double displacedMass = submerged * world.fluidField().density(body.transform().position());
        double error = Math.abs(displacedMass - targetMass);
        if (error < bestError) {
          bestError = error;
          bestY = y;
        }
        if (error <= config.massTolerance()) {
          return new EquilibriumResult(true, bestY - originY, error, "ok");
        }
      }
    } finally {
      body.setTransform(original);
    }
    return EquilibriumResult.none("no equilibrium in range");
  }
}
