package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class VegetationDragForceTest {
  @Test
  void stillBodyInKelpProducesNoForce() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World kelp = vegetated(1.0);
    VegetationDragForce drag = new VegetationDragForce(2.0);
    assertEquals(0.0, drag.apply(body, kelp).force().length(), 0.0);
  }

  @Test
  void movingThroughKelpOpposesVelocity() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(0, 0, 4));
    Force.Result result = new VegetationDragForce(2.0).apply(body, vegetated(1.0));
    assertEquals(0.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertTrue(result.force().z() < 0);
    assertEquals(-2.0 * 4.0 * 4.0, result.force().z(), 1e-9);
  }

  @Test
  void clearWaterProducesNoVegetationDrag() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(0, 0, 4));
    assertEquals(0.0, new VegetationDragForce(2.0).apply(body, vegetated(0.0)).force().length(), 0.0);
  }

  private static World vegetated(double occupancy) {
    return new World() {
      public Vector3dc gravity() {
        return new Vector3d();
      }

      public FluidField fluidField() {
        return PhysFixtures.vacuum();
      }

      public double timeStep() {
        return 0.05;
      }

      public double vegetation(Vector3dc point) {
        return occupancy;
      }
    };
  }
}
