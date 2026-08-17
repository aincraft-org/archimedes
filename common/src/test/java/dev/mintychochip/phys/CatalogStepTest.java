package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class CatalogStepTest {
  @Test
  void gravityStepChangesVerticalVelocity() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new GravityForce()));
    World world = PhysFixtures.world(0.2, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    double expectedVy =
        new GravityForce().apply(body, world).force().y() * body.inverseMass() * world.timeStep();

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(expectedVy, body.linearVelocity().y(), 1e-9);
    assertTrue(expectedVy < 0);
    assertTrue(body.transform().position().y() < 0);
  }

  @Test
  void buoyancyAloneStepsNetUp() {
    Collider envelope = PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(envelope),
            List.of(new FluidBuoyancyForce(DensityField.uniform(1.2))));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertTrue(body.linearVelocity().y() > 0);
    assertTrue(body.transform().position().y() > 0);
  }

  @Test
  void quadraticDragStepReducesSpeedAndDiffersFromViscous() {
    QuadraticDragForce quadratic = new QuadraticDragForce(2);
    ViscousDragForce viscous = new ViscousDragForce(2);
    BodyImpl quad =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(quadratic));
    BodyImpl lin =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(viscous));
    quad.setLinearVelocity(new Vector3d(3, 0, 0));
    lin.setLinearVelocity(new Vector3d(3, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(quad, lin));

    assertTrue(quad.linearVelocity().x() < 3);
    assertTrue(lin.linearVelocity().x() < 3);
    assertTrue(quad.linearVelocity().x() < lin.linearVelocity().x());
    assertTrue(quad.linearVelocity().x() > 0);
  }

  @Test
  void thrustStepChangesVelocityAlongItsDirection() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new ThrustForce(new Vector3d(0, 0, 1), 10)));
    World world = PhysFixtures.world(0.2, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(0.0, body.linearVelocity().x(), 1e-9);
    assertEquals(0.0, body.linearVelocity().y(), 1e-9);
    assertEquals(1.0, body.linearVelocity().z(), 1e-9);
    assertTrue(body.transform().position().z() > 0);
  }

  @Test
  void isolatedLiftStepIsZeroAtRestAndLargerWithForwardSpeed() {
    LiftForce lift = new LiftForce(new Vector3d(0, 1, 0), 0.5);
    BodyImpl rest =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(lift));
    BodyImpl slow =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(lift));
    BodyImpl fast =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(lift));
    slow.setLinearVelocity(new Vector3d(4, 0, 0));
    fast.setLinearVelocity(new Vector3d(8, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(rest, slow, fast));

    assertEquals(0.0, rest.linearVelocity().y(), 1e-9);
    assertTrue(slow.linearVelocity().y() > rest.linearVelocity().y());
    assertTrue(fast.linearVelocity().y() > slow.linearVelocity().y());
  }

  @Test
  void kineticFrictionWithNoContactLoadDoesNotChangeTangentSpeed() {
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(0, 5, 0), new Quaterniond()),
            2,
            List.of(),
            List.of(new CoulombFrictionForce(floor, 0.8, 0.5)));
    body.setLinearVelocity(new Vector3d(4, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(4.0, body.linearVelocity().x(), 1e-9);
  }

  @Test
  void mixedCollectionKeepsVehicleContactAndDampingSignatures() {
    FluidField water = PhysFixtures.liquidBelow(10, 1000);
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), water);
    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));

    BodyImpl watercraft =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            500,
            List.of(PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5))),
            List.of(new GravityForce(), new FluidBuoyancyForce()));
    BodyImpl airship =
        new BodyImpl(
            new Transform(new Vector3d(0, 40, 0), new Quaterniond()),
            1000,
            List.of(PhysFixtures.box(new Vector3d(), new Vector3d(5, 5, 5))),
            List.of(new GravityForce(), new FluidBuoyancyForce(DensityField.uniform(1.2))));
    LiftForce lift = new LiftForce(new Vector3d(0, 1, 0), 2);
    BodyImpl planeRest =
        new BodyImpl(
            new Transform(new Vector3d(20, 8, 0), new Quaterniond()),
            10,
            List.of(),
            List.of(new GravityForce(), lift));
    BodyImpl planeFast =
        new BodyImpl(
            new Transform(new Vector3d(20, 8, 0), new Quaterniond()),
            10,
            List.of(),
            List.of(new GravityForce(), lift));
    planeFast.setLinearVelocity(new Vector3d(10, 0, 0));
    BodyImpl withFriction =
        new BodyImpl(
            new Transform(new Vector3d(30, 0, 0), new Quaterniond()),
            2,
            List.of(),
            List.of(
                new GravityForce(),
                new SupportForce(floor),
                new CoulombFrictionForce(floor, 0.5, 0.4)));
    BodyImpl withoutFriction =
        new BodyImpl(
            new Transform(new Vector3d(32, 0, 0), new Quaterniond()),
            2,
            List.of(),
            List.of(new GravityForce(), new SupportForce(floor)));
    withFriction.setLinearVelocity(new Vector3d(5, 0, 0));
    withoutFriction.setLinearVelocity(new Vector3d(5, 0, 0));
    BodyImpl spinner =
        new BodyImpl(
            new Transform(new Vector3d(40, 10, 0), new Quaterniond()),
            2,
            List.of(),
            List.of(new AngularDragForce(4)));
    spinner.setAngularVelocity(new Vector3d(0, 6, 0));
    double omega0 = spinner.angularVelocity().length();
    double restLift = lift.apply(planeRest, world).force().y();
    double speedLift = lift.apply(planeFast, world).force().y();

    new PhysicsEngine()
        .step(
            world,
            List.of(
                watercraft, airship, planeRest, planeFast, withFriction, withoutFriction, spinner));

    assertTrue(watercraft.linearVelocity().y() > 0);
    assertTrue(airship.linearVelocity().y() > 0);
    assertEquals(0.0, restLift, 0.0);
    assertTrue(speedLift > restLift);
    assertTrue(planeRest.linearVelocity().y() < 0);
    assertTrue(planeFast.linearVelocity().y() > planeRest.linearVelocity().y());
    assertEquals(0.0, withFriction.linearVelocity().y(), 1e-9);
    assertTrue(withFriction.linearVelocity().x() < withoutFriction.linearVelocity().x());
    assertTrue(spinner.angularVelocity().length() < omega0);
  }
}
