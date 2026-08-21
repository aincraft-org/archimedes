package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class CenterOfMassIntegrationTest {
  @Test
  void pureTorqueRotatesAboutCenterOfMassNotOrigin() {
    Force torque = (b, w) -> new Force.Result(new Vector3d(), new Vector3d(4, 0, 0));
    BodyImpl body = rodFromOrigin(torque);
    World world = PhysFixtures.world(0.05, new Vector3d(), PhysFixtures.vacuum());
    Vector3d com0 = MassProperties.worldCenterOfMass(body);
    PhysicsEngine engine = new PhysicsEngine();
    for (int i = 0; i < 20; i++) {
      engine.step(world, List.of(body));
    }
    Vector3d com1 = MassProperties.worldCenterOfMass(body);
    assertEquals(com0.x(), com1.x(), 1e-6);
    assertEquals(com0.y(), com1.y(), 1e-6);
    assertEquals(com0.z(), com1.z(), 1e-6);
    assertTrue(Math.abs(body.transform().orientation().w() - 1.0) > 1e-6);
    assertTrue(body.transform().position().distance(new Vector3d()) > 1e-4);
  }

  @Test
  void freeFallKeepsOffsetComAndOriginFallingTogetherWithoutSpin() {
    BodyImpl body = rodFromOrigin(new GravityForce());
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    double originY0 = body.transform().position().y();
    double comY0 = MassProperties.worldCenterOfMass(body).y();
    PhysicsEngine engine = new PhysicsEngine();
    for (int i = 0; i < 20; i++) {
      engine.step(world, List.of(body));
    }
    assertEquals(1.0, body.transform().orientation().w(), 1e-9);
    assertEquals(0.0, body.transform().orientation().x(), 1e-9);
    assertEquals(0.0, body.transform().orientation().y(), 1e-9);
    assertEquals(0.0, body.transform().orientation().z(), 1e-9);
    Vector3d origin1 = new Vector3d(body.transform().position());
    Vector3d com1 = MassProperties.worldCenterOfMass(body);
    assertTrue(origin1.y() < originY0);
    assertTrue(com1.y() < comY0);
    assertEquals(com1.y(), origin1.y(), 1e-6);
    assertEquals(com1.z() - 1.5, origin1.z(), 1e-6);
  }

  private static BodyImpl rodFromOrigin(Force force) {
    Aabb cube = new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    Collider near =
        new ColliderImpl(
            cube, new Material(1), new Transform(new Vector3d(0, 0, 0.5), new Quaterniond()));
    Collider far =
        new ColliderImpl(
            cube, new Material(1), new Transform(new Vector3d(0, 0, 2.5), new Quaterniond()));
    return new BodyImpl(
        new Transform(new Vector3d(), new Quaterniond()), 2, List.of(near, far), List.of(force));
  }
}
