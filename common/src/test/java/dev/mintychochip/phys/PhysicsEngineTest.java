package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void stepsAllSixDegreesOfFreedomWithoutVehicleTypeAssumptions() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of((b, w) -> new Force.Result(new Vector3d(4, 0, 6), new Vector3d(0, 2, 0))));
    World world = world(0.5);
    Vector3d start = new Vector3d(body.transform().position());
    Quaterniond startQ = new Quaterniond(body.transform().orientation());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(1.0, body.linearVelocity().x(), 1e-9);
    assertEquals(0.0, body.linearVelocity().y(), 1e-9);
    assertEquals(1.5, body.linearVelocity().z(), 1e-9);
    assertTrue(body.transform().position().x() > start.x());
    assertTrue(body.transform().position().z() > start.z());
    assertEquals(0.0, body.angularVelocity().x(), 1e-9);
    assertTrue(body.angularVelocity().y() > 0);
    assertTrue(Math.abs(body.transform().orientation().y()) > 0);
    assertNotEquals(startQ, body.transform().orientation());
  }

  @Test
  void skipsInactiveBodiesWithoutChangingState() {
    BodyImpl inactive =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(),
            List.of((b, w) -> new Force.Result(new Vector3d(10, 10, 10), new Vector3d(4, 0, 0))));
    inactive.setActive(false);
    inactive.setLinearVelocity(new Vector3d(1, 0, 0));
    inactive.setAngularVelocity(new Vector3d(0, 2, 0));
    Transform start = inactive.transform();
    World world = world(0.5);

    new PhysicsEngine().step(world, List.of(inactive));

    assertEquals(new Vector3d(1, 0, 0), inactive.linearVelocity());
    assertEquals(new Vector3d(0, 2, 0), inactive.angularVelocity());
    assertEquals(start, inactive.transform());
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
    assertTrue(Math.abs(body.transform().orientation().x()) > 0);
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
