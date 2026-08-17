package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class VehicleCompositionTest {
  @Test
  void watercraftGetsNetUpWhenDisplacedLiquidWeighsMoreThanTheBody() {
    FluidField water = PhysFixtures.liquidBelow(10, 1000);
    Collider hull = PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    GravityForce gravity = new GravityForce();
    FluidBuoyancyForce buoyancy = new FluidBuoyancyForce();
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            500,
            List.of(hull),
            List.of(gravity, buoyancy));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), water);

    double netY = gravity.apply(body, world).force().y() + buoyancy.apply(body, world).force().y();
    new PhysicsEngine().step(world, List.of(body));

    assertTrue(netY > 0, "displaced liquid 1000 kg > body 500 kg");
    assertTrue(body.linearVelocity().y() > 0);
    assertTrue(body.transform().position().y() > 0);
  }

  @Test
  void airshipGetsNetUpFromAerostaticLiftAtZeroAirspeed() {
    Collider envelope = PhysFixtures.box(new Vector3d(), new Vector3d(5, 5, 5));
    GravityForce gravity = new GravityForce();
    FluidBuoyancyForce aerostatic = new FluidBuoyancyForce(DensityField.uniform(1.2));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(0, 40, 0), new Quaterniond()),
            1000,
            List.of(envelope),
            List.of(gravity, aerostatic));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    assertEquals(0.0, body.linearVelocity().length(), 0.0);
    assertFalse(world.fluidField().isFluid(body.transform().position()));
    double netY =
        gravity.apply(body, world).force().y() + aerostatic.apply(body, world).force().y();
    new PhysicsEngine().step(world, List.of(body));

    assertTrue(netY > 0, "displaced air 1200 kg > body 1000 kg at rest");
    assertTrue(body.linearVelocity().y() > 0);
  }

  @Test
  void airplaneLiftIsNearZeroAtRestAndGrowsWithForwardSpeed() {
    LiftForce lift = new LiftForce(new Vector3d(0, 1, 0), 2);
    GravityForce gravity = new GravityForce();
    BodyImpl rest =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            10,
            List.of(),
            List.of(gravity, lift));
    BodyImpl flying =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            10,
            List.of(),
            List.of(gravity, lift));
    flying.setLinearVelocity(new Vector3d(10, 0, 0));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    double restLift = lift.apply(rest, world).force().y();
    double speedLift = lift.apply(flying, world).force().y();
    new PhysicsEngine().step(world, List.of(rest, flying));

    assertEquals(0.0, restLift, 0.0);
    assertTrue(speedLift > restLift);
    assertTrue(rest.linearVelocity().y() < 0, "airplane at rest is not held up by lift");
    assertTrue(flying.linearVelocity().y() > rest.linearVelocity().y());
  }

  @Test
  void samePrimitivesComposeDragAndThrustOnTheGenericStep() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new ThrustForce(new Vector3d(1, 0, 0), 8), new QuadraticDragForce(0)));
    World world = PhysFixtures.world(0.5, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(2.0, body.linearVelocity().x(), 1e-9);
    assertEquals(0.0, body.linearVelocity().y(), 1e-9);
    assertEquals(1.0, body.transform().position().x(), 1e-9);
  }
}
