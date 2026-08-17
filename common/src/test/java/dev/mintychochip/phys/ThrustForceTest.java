package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ThrustForceTest {
  @Test
  void appliesMagnitudeAlongBodyLocalDirection() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new ThrustForce(new Vector3d(1, 0, 0), 12).apply(body, world);

    assertEquals(12.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertEquals(0.0, result.torque().length(), 1e-9);
  }

  @Test
  void rotatesWithBodyOrientation() {
    Quaterniond yaw = new Quaterniond().rotateY(Math.PI / 2);
    BodyImpl body = new BodyImpl(new Transform(new Vector3d(), yaw), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new ThrustForce(new Vector3d(1, 0, 0), 10).apply(body, world);

    assertEquals(0.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertEquals(-10.0, result.force().z(), 1e-9);
  }
}
