package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class LiftingSailForceTest {
  @Test
  void stillAirProducesZeroForce() {
    LiftingSailForce sail = sail(FlowField.still());
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    Force.Result result = sail.apply(body, world);
    assertEquals(0.0, result.force().length(), 0.0);
  }

  @Test
  void vacuumProducesZeroForce() {
    LiftingSailForce sail =
        new LiftingSailForce(
            new Vector3d(),
            new Vector3d(0, 0, 1),
            new Vector3d(0, 1, 0),
            10,
            DensityField.uniform(0),
            FlowField.uniform(new Vector3d(0, 0, 10)));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    assertEquals(0.0, sail.apply(body, world).force().length(), 0.0);
  }

  @Test
  void restInABreezeProducesForceUnlikeLiftForce() {
    FlowField wind = FlowField.uniform(new Vector3d(8, 0, -8));
    LiftingSailForce sail = sail(wind);
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    assertTrue(sail.apply(body, world).force().length() > 0);
    assertEquals(
        0.0, new LiftForce(new Vector3d(0, 1, 0), 2).apply(body, world).force().length(), 0.0);
  }

  @Test
  void closeHauledForceHasForwardDriveAndABeamLoad() {
    FlowField wind = FlowField.uniform(new Vector3d(8, 0, -8));
    LiftingSailForce sail = sail(wind);
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    Force.Result result = sail.apply(body, world);
    assertTrue(result.force().z() > 0, "drive along the chord (bow)");
    assertTrue(Math.abs(result.force().x()) > 1e-6, "beam load for the keel to cancel");
  }

  @Test
  void restInABreezeMovesOnStep() {
    FlowField wind = FlowField.uniform(new Vector3d(8, 0, -8));
    LiftingSailForce sail = sail(wind);
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 20, List.of(), List.of(sail));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    new PhysicsEngine().step(world, List.of(body));
    assertTrue(body.linearVelocity().length() > 0);
  }

  private static LiftingSailForce sail(FlowField wind) {
    return new LiftingSailForce(
        new Vector3d(),
        new Vector3d(0, 0, 1),
        new Vector3d(0, 1, 0),
        10,
        DensityField.uniform(1.2),
        wind);
  }
}
