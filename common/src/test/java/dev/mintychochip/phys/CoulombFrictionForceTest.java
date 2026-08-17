package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class CoulombFrictionForceTest {
  @Test
  void kineticFrictionOpposesTangentSlip() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(4, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new CoulombFrictionForce(floor, 0.8, 0.5).apply(body, world);

    // N = 2 * 10 = 20; kinetic = μ_k N = 10 opposing +x
    assertEquals(-10.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertEquals(0.0, result.torque().length(), 1e-9);
  }

  @Test
  void frictionIsZeroWithNoContactLoad() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(0, 5, 0), new Quaterniond()), 2, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(4, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new CoulombFrictionForce(floor, 0.8, 0.5).apply(body, world);

    assertEquals(0.0, result.force().length(), 1e-9);
  }

  @Test
  void staticFrictionHoldsWhenTangentLoadIsBelowThreshold() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    ThrustForce push = new ThrustForce(new Vector3d(1, 0, 0), 8);
    CoulombFrictionForce friction = new CoulombFrictionForce(floor, 0.6, 0.4);
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new GravityForce(), new SupportForce(floor), push, friction));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    // μ_s N = 0.6 * 20 = 12 > 8
    Force.Result result = friction.apply(body, world);
    new PhysicsEngine().step(world, List.of(body));

    assertEquals(-8.0, result.force().x(), 1e-9);
    assertEquals(0.0, body.linearVelocity().length(), 1e-9);
  }

  @Test
  void staticFrictionDoesNotHoldWhenTangentLoadExceedsThreshold() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    ThrustForce push = new ThrustForce(new Vector3d(1, 0, 0), 16);
    CoulombFrictionForce friction = new CoulombFrictionForce(floor, 0.6, 0.4);
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new GravityForce(), new SupportForce(floor), push, friction));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    // μ_s N = 12 < 16; kinetic μ_k N = 8 opposing
    Force.Result result = friction.apply(body, world);
    new PhysicsEngine().step(world, List.of(body));

    assertEquals(-8.0, result.force().x(), 1e-9);
    assertTrue(body.linearVelocity().x() > 0);
  }
}
