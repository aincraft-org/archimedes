package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class KeelForceTest {
  @Test
  void surgeAlongTheKeelProducesNoForce() {
    KeelForce keel =
        new KeelForce(new Vector3d(1, 0, 0), 2, DensityField.uniform(1), FlowField.still());
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(0, 0, 5));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    assertEquals(0.0, keel.apply(body, world).force().length(), 1e-12);
  }

  @Test
  void sideslipIsOpposedAlongTheBeam() {
    KeelForce keel =
        new KeelForce(new Vector3d(1, 0, 0), 2, DensityField.uniform(1), FlowField.still());
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(3, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    Force.Result result = keel.apply(body, world);
    assertEquals(-18.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().z(), 1e-12);
  }

  @Test
  void stepBleedsSideslipFasterThanSurge() {
    KeelForce keel =
        new KeelForce(new Vector3d(1, 0, 0), 0.4, DensityField.uniform(1), FlowField.still());
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(keel));
    body.setLinearVelocity(new Vector3d(4, 0, 4));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    new PhysicsEngine().step(world, List.of(body));
    assertTrue(body.linearVelocity().x() < body.linearVelocity().z());
    assertTrue(body.linearVelocity().z() > 3.9);
  }
}
