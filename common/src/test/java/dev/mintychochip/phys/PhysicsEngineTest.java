package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class PhysicsEngineTest {
  @Test
  void stepsSuppliedBodiesAndDoesNotInjectGravity() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(),
            List.of((b, w) -> new Force.Result(new Vector3d(2, 0, 0), new Vector3d())));
    World world = world(0.5);
    new PhysicsEngine().step(world, List.of(body));
    assertEquals(new Vector3d(1, 0, 0), body.linearVelocity());
    assertEquals(new Vector3d(0.5, 0, 0), body.transform().position());
  }

  @Test
  void integratesTorqueIntoAngularVelocityAndOrientation() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(),
            List.of((b, w) -> new Force.Result(new Vector3d(), new Vector3d(2, 0, 0))));
    World world = world(0.5);
    new PhysicsEngine().step(world, List.of(body));
    assertEquals(new Vector3d(1, 0, 0), body.angularVelocity());
    assertNotEquals(new Quaterniond(), body.transform().orientation());
  }

  private static World world(double dt) {
    return new World() {
      public Vector3d gravity() {
        return new Vector3d(0, -100, 0);
      }

      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3dc p) {
            return false;
          }

          public double density(Vector3dc p) {
            return 0;
          }
        };
      }

      public double timeStep() {
        return dt;
      }
    };
  }
}
