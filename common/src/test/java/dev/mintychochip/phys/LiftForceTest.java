package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class LiftForceTest {
  @Test
  void restProducesNoLift() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new LiftForce(new Vector3d(0, 1, 0), 2).apply(body, world);

    assertEquals(0.0, result.force().length(), 0.0);
  }

  @Test
  void forwardSpeedProducesUpwardLiftThatGrowsWithAirspeed() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    LiftForce lift = new LiftForce(new Vector3d(0, 1, 0), 0.5);

    body.setLinearVelocity(new Vector3d(4, 0, 0));
    double slow = lift.apply(body, world).force().y();
    body.setLinearVelocity(new Vector3d(8, 0, 0));
    double fast = lift.apply(body, world).force().y();

    assertEquals(8.0, slow, 1e-9);
    assertEquals(32.0, fast, 1e-9);
    assertTrue(fast > slow);
  }

  @Test
  void verticalVelocityAloneDoesNotCreateLift() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(0, -20, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new LiftForce(new Vector3d(0, 1, 0), 4).apply(body, world);

    assertEquals(0.0, result.force().length(), 1e-9);
  }
}
