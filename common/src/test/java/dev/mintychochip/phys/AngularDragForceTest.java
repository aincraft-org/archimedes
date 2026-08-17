package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class AngularDragForceTest {
  @Test
  void restProducesZeroTorque() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new AngularDragForce(3).apply(body, world);

    assertEquals(0.0, result.torque().length(), 0.0);
    assertEquals(0.0, result.force().length(), 0.0);
  }

  @Test
  void torqueOpposesAngularVelocity() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setAngularVelocity(new Vector3d(0, 2, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new AngularDragForce(5).apply(body, world);

    assertEquals(-10.0, result.torque().y(), 1e-9);
    assertEquals(0.0, result.force().length(), 1e-9);
  }

  @Test
  void stepReducesSpinRate() {
    AngularDragForce drag = new AngularDragForce(4);
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of(drag));
    body.setAngularVelocity(new Vector3d(0, 6, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    double before = body.angularVelocity().length();

    new PhysicsEngine().step(world, List.of(body));

    assertTrue(body.angularVelocity().length() < before);
    assertTrue(body.angularVelocity().y() > 0);
  }
}
