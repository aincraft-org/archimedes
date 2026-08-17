package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ViscousDragForceTest {
  @Test
  void restProducesZeroDrag() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new ViscousDragForce(3).apply(body, world);

    assertEquals(0.0, result.force().length(), 0.0);
  }

  @Test
  void opposesVelocityLinearlyAndIsDistinctFromQuadratic() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(4, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result viscous = new ViscousDragForce(2).apply(body, world);
    Force.Result quadratic = new QuadraticDragForce(2).apply(body, world);

    assertEquals(-8.0, viscous.force().x(), 1e-9);
    assertEquals(-32.0, quadratic.force().x(), 1e-9);
    assertTrue(Math.abs(viscous.force().x()) < Math.abs(quadratic.force().x()));
  }

  @Test
  void stepReducesLinearSpeed() {
    ViscousDragForce drag = new ViscousDragForce(4);
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of(drag));
    body.setLinearVelocity(new Vector3d(5, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    double before = body.linearVelocity().length();

    new PhysicsEngine().step(world, List.of(body));

    assertTrue(body.linearVelocity().length() < before);
    assertTrue(body.linearVelocity().x() > 0);
  }
}
