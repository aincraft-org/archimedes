package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class SupportForceTest {
  @Test
  void cancelsCompressiveGravityLoadWhenInContact() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new SupportForce(floor).apply(body, world);

    assertEquals(0.0, result.force().x(), 1e-9);
    assertEquals(20.0, result.force().y(), 1e-9);
    assertEquals(0.0, result.torque().length(), 1e-9);
  }

  @Test
  void appliesNoLoadWhenAboveThePlane() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(0, 4, 0), new Quaterniond()), 2, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new SupportForce(floor).apply(body, world);

    assertEquals(0.0, result.force().length(), 1e-9);
  }

  @Test
  void gravityPlusSupportDoesNotAccelerateThroughThePlane() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            3,
            List.of(),
            List.of(new GravityForce(), new SupportForce(floor)));
    World world = PhysFixtures.world(0.2, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(0.0, body.linearVelocity().y(), 1e-9);
    assertEquals(0.0, body.transform().position().y(), 1e-9);
    assertTrue(body.transform().position().y() >= 0.0);
  }
}
