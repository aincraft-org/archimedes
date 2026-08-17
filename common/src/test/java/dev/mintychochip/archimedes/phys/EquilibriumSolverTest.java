package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.phys.Aabb;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.BodyImpl;
import dev.mintychochip.phys.Collider;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.Material;
import dev.mintychochip.phys.Shape;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.World;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class EquilibriumSolverTest {
  @Test
  void equalBuoyancyAndWeightIsEquilibrium() {
    Collider collider =
        new Collider() {
          public Shape shape() {
            return new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
          }

          public Material material() {
            return new Material(1000);
          }

          public Transform localTransform() {
            return new Transform(new Vector3d(), new Quaterniond());
          }
        };
    Body body =
        new BodyImpl(
            new Transform(new Vector3d(0, 0, 0), new Quaterniond()),
            1000,
            List.of(collider),
            List.of());
    World world =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -10, 0);
          }

          public FluidField fluidField() {
            return new FluidField() {
              public boolean isFluid(Vector3dc p) {
                return p.y() <= 10.5;
              }

              public double density(Vector3dc p) {
                return 1000;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };
    ShipConfig config =
        new ShipConfig(
            2048, 8, Set.of(), Set.of(), true, 1, 0.5, 16.0, 0.05, 1.0, 0.5, 0.9, Map.of(), 1.0,
            80.0, 16.0, 1e-6, 1e-3);
    EquilibriumResult result = new EquilibriumSolver().solve(body, world, config);
    assertTrue(result.equilibrium());
  }
}
