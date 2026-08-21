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

  @Test
  void heavyBodyAgainstInactiveWallDoesNotBounce() {
    BodyImpl wall = boxBody(new Vector3d(-1, 0, 0));
    wall.setActive(false);
    BodyImpl heavy =
        new BodyImpl(
            new Transform(new Vector3d(0.0, 0, 0), new Quaterniond()),
            4,
            List.of(PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5))),
            List.of());
    heavy.setLinearVelocity(new Vector3d(-2, 0, 0));
    World world = PhysFixtures.world(0.05, new Vector3d(), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(heavy, wall));

    assertTrue(
        heavy.linearVelocity().x() > -0.25,
        "closing speed must be removed; vx=" + heavy.linearVelocity().x());
    assertTrue(
        heavy.linearVelocity().x() < 0.5,
        "mass-4 body must not reverse; vx=" + heavy.linearVelocity().x());
  }

  @Test
  void offsetBodyBodyContactChangesAngularVelocity() {
    // Inactive ground cube under the +Z end of a two-cell rod along Z.
    // Rod: unit cubes at local (0,0,2) and (0,0,-2), mass 2, origin at (0, 0.4, 0)
    // so the +Z cube sits around y=0.4. Ground: unit cube at (0, -0.4, 2), inactive.
    // They overlap in Y. Rod vy = -2 (closing).
    BodyImpl ground = boxBody(new Vector3d(0, -0.4, 2));
    ground.setActive(false);
    BodyImpl rod = horizontalRod(new Vector3d(0, 0.4, 0));
    rod.setLinearVelocity(new Vector3d(0, -2, 0));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    new PhysicsEngine().step(world, List.of(rod, ground));
    assertTrue(Math.abs(rod.angularVelocity().x()) > 1e-6, "offset support must pitch the rod");
  }

  private static BodyImpl boxBody(Vector3d position) {
    return new BodyImpl(
        new Transform(position, new Quaterniond()),
        1,
        List.of(PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5))),
        List.of());
  }

  private static BodyImpl horizontalRod(Vector3d position) {
    Vector3d half = new Vector3d(0.5, 0.5, 0.5);
    return new BodyImpl(
        new Transform(position, new Quaterniond()),
        2,
        List.of(
            PhysFixtures.box(new Vector3d(0, 0, 2), half),
            PhysFixtures.box(new Vector3d(0, 0, -2), half)),
        List.of());
  }
}
