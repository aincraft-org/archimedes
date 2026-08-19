package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class GyroscopicIntegrationTest {
  @Test
  void anisotropicSpinWithTwoAxisOmegaProducesThirdAxisRate() {
    Aabb rod = new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 5));
    double volume = 8 * 0.5 * 0.5 * 5;
    Collider collider =
        new ColliderImpl(rod, new Material(1), new Transform(new Vector3d(), new Quaterniond()));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), volume, List.of(collider), List.of());
    body.setAngularVelocity(new Vector3d(1, 0, 1));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertTrue(Math.abs(body.angularVelocity().y()) > 1e-6);
  }

  @Test
  void isotropicSpinKeepsOmegaWhenUntorqued() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 4, List.of(), List.of());
    body.setAngularVelocity(new Vector3d(1, 1, 0));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(1.0, body.angularVelocity().x(), 1e-9);
    assertEquals(1.0, body.angularVelocity().y(), 1e-9);
    assertEquals(0.0, body.angularVelocity().z(), 1e-9);
  }
}
