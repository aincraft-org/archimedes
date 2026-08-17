package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicsEngineTest {
  @Test void stepsSuppliedBodiesAndDoesNotInjectGravity() {
    BodyImpl body = new BodyImpl(
        new Transform(Vector3.ZERO, new Quaternion(0, 0, 0, 1)),
        1,
        List.of(),
        List.of((b, w) -> new Force.Result(new Vector3(2, 0, 0), Vector3.ZERO)));
    World world = world(0.5);
    new PhysicsEngine().step(world, List.of(body));
    assertEquals(new Vector3(1, 0, 0), body.linearVelocity());
    assertEquals(new Vector3(0.5, 0, 0), body.transform().position());
  }

  @Test void integratesTorqueIntoAngularVelocityAndOrientation() {
    BodyImpl body = new BodyImpl(
        new Transform(Vector3.ZERO, new Quaternion(0, 0, 0, 1)),
        1,
        List.of(),
        List.of((b, w) -> new Force.Result(Vector3.ZERO, new Vector3(2, 0, 0))));
    World world = world(0.5);
    new PhysicsEngine().step(world, List.of(body));
    assertEquals(new Vector3(1, 0, 0), body.angularVelocity());
    assertNotEquals(new Quaternion(0, 0, 0, 1), body.transform().orientation());
  }

  private static World world(double dt) {
    return new World() {
      public Vector3 gravity() { return new Vector3(0, -100, 0); }
      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3 p) { return false; }
          public double density(Vector3 p) { return 0; }
        };
      }
      public double timeStep() { return dt; }
    };
  }
}
