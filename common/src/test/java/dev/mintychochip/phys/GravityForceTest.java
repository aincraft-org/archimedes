package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class GravityForceTest {
  @Test
  void applyReturnsMassTimesWorldGravityAndZeroTorque() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 4, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new GravityForce().apply(body, world);

    assertEquals(new Vector3d(0, -40, 0), result.force());
    assertEquals(new Vector3d(), result.torque());
  }

  @Test
  void engineDoesNotInjectGravityUnlessGravityForceIsAttached() {
    BodyImpl bare =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 4, List.of(), List.of());
    BodyImpl weighted =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            4,
            List.of(),
            List.of(new GravityForce()));
    World world = PhysFixtures.world(0.5, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(bare, weighted));

    assertEquals(new Vector3d(), bare.linearVelocity());
    assertEquals(new Vector3d(0, -5, 0), weighted.linearVelocity());
  }
}
