package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ContactCompositionTest {
  @Test
  void gravitySupportAndFrictionKeepTheBodyOnThePlaneAndBleedTangentSpeed() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    GravityForce gravity = new GravityForce();
    SupportForce support = new SupportForce(floor);
    CoulombFrictionForce friction = new CoulombFrictionForce(floor, 0.5, 0.4);
    BodyImpl withFriction =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(gravity, support, friction));
    BodyImpl withoutFriction =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(gravity, support));
    withFriction.setLinearVelocity(new Vector3d(5, 0, 0));
    withoutFriction.setLinearVelocity(new Vector3d(5, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(withFriction, withoutFriction));

    assertEquals(0.0, withFriction.linearVelocity().y(), 1e-9);
    assertEquals(0.0, withFriction.transform().position().y(), 1e-9);
    assertTrue(withFriction.transform().position().y() >= 0.0);
    assertTrue(withFriction.linearVelocity().x() < withoutFriction.linearVelocity().x());
    assertEquals(5.0, withoutFriction.linearVelocity().x(), 1e-9);
  }
}
