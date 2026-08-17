package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class CollisionTest {
  @Test
  void detectFindsOverlappingBodiesViaTheOctree() {
    BodyImpl left = boxBody(new Vector3d(0, 0, 0));
    BodyImpl right = boxBody(new Vector3d(0.5, 0, 0));
    BodyImpl far = boxBody(new Vector3d(20, 0, 0));

    List<Contact> contacts = Collisions.detect(List.of(left, right, far));

    assertEquals(1, contacts.size());
    assertTrue(contacts.get(0).penetration() > 0);
  }

  @Test
  void stepSeparatesOverlappingBodies() {
    BodyImpl left = boxBody(new Vector3d(0, 0, 0));
    BodyImpl right = boxBody(new Vector3d(0.4, 0, 0));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(left, right));

    double gap = right.transform().position().x() - left.transform().position().x() - 1.0;
    assertTrue(gap >= -1e-6, "unit boxes must not remain interpenetrating");
  }

  private static BodyImpl boxBody(Vector3d position) {
    return new BodyImpl(
        new Transform(position, new Quaterniond()),
        1,
        List.of(PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5))),
        List.of());
  }
}
