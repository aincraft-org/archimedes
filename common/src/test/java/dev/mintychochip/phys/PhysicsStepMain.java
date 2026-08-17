package dev.mintychochip.phys;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Library-consumer entry: steps every shipped catalog force and a mixed collection on the real
 * engine, then prints the defining post-step observables.
 */
public final class PhysicsStepMain {
  private PhysicsStepMain() {}

  public static void main(String[] args) {
    perForce();
    mixed();
  }

  private static void perForce() {
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PhysicsEngine engine = new PhysicsEngine();

    BodyImpl gravity =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new GravityForce()));
    engine.step(world, List.of(gravity));

    BodyImpl buoyancy =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5))),
            List.of(new FluidBuoyancyForce(DensityField.uniform(1.2))));
    engine.step(world, List.of(buoyancy));

    BodyImpl quad =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(),
            List.of(new QuadraticDragForce(2)));
    BodyImpl visc =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(),
            List.of(new ViscousDragForce(2)));
    quad.setLinearVelocity(new Vector3d(3, 0, 0));
    visc.setLinearVelocity(new Vector3d(3, 0, 0));
    engine.step(world, List.of(quad, visc));

    BodyImpl thrust =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new ThrustForce(new Vector3d(0, 0, 1), 10)));
    engine.step(world, List.of(thrust));

    LiftForce lift = new LiftForce(new Vector3d(0, 1, 0), 0.5);
    BodyImpl rest =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(lift));
    BodyImpl fast =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(lift));
    fast.setLinearVelocity(new Vector3d(8, 0, 0));
    engine.step(world, List.of(rest, fast));

    ContactPlane floor = new ContactPlane(new Vector3d(), new Vector3d(0, 1, 0));
    BodyImpl airborne =
        new BodyImpl(
            new Transform(new Vector3d(0, 5, 0), new Quaterniond()),
            2,
            List.of(),
            List.of(new CoulombFrictionForce(floor, 0.8, 0.5)));
    airborne.setLinearVelocity(new Vector3d(4, 0, 0));
    engine.step(world, List.of(airborne));

    BodyImpl spinner =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(new AngularDragForce(4)));
    spinner.setAngularVelocity(new Vector3d(0, 6, 0));
    double omega0 = 6.0;
    engine.step(world, List.of(spinner));

    System.out.printf(
        "perForce gravityVy=%.4f buoyancyVy=%.4f quadVx=%.4f viscVx=%.4f thrustVz=%.4f"
            + " restLiftVy=%.4f speedLiftVy=%.4f noLoadVx=%.4f omega1=%.4f omega0=%.4f%n",
        gravity.linearVelocity().y(),
        buoyancy.linearVelocity().y(),
        quad.linearVelocity().x(),
        visc.linearVelocity().x(),
        thrust.linearVelocity().z(),
        rest.linearVelocity().y(),
        fast.linearVelocity().y(),
        airborne.linearVelocity().x(),
        spinner.angularVelocity().length(),
        omega0);
  }

  private static void mixed() {
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
    double restLift = lift.apply(planeRest, world).force().y();
    double speedLift = lift.apply(planeFast, world).force().y();
    new PhysicsEngine()
        .step(
            world,
            List.of(
                watercraft, airship, planeRest, planeFast, withFriction, withoutFriction, spinner));
    System.out.printf(
        "mixed watercraftVy=%.4f airshipVy=%.4f restLift=%.4f speedLift=%.4f planeRestVy=%.4f"
            + " planeFastVy=%.4f withVx=%.4f withoutVx=%.4f omega=%.4f%n",
        watercraft.linearVelocity().y(),
        airship.linearVelocity().y(),
        restLift,
        speedLift,
        planeRest.linearVelocity().y(),
        planeFast.linearVelocity().y(),
        withFriction.linearVelocity().x(),
        withoutFriction.linearVelocity().x(),
        spinner.angularVelocity().length());
  }
}
