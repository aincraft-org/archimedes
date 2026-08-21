package dev.mintychochip.phys;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Library-consumer entry: composes watercraft, airship, and airplane force lists on the shipped
 * engine and prints the defining observables.
 */
public final class VehicleModelsMain {
  private VehicleModelsMain() {}

  /**
   * Prints vehicle-model samples.
   *
   * @param args unused
   */
  public static void main(String[] args) {
    watercraft();
    airship();
    airplane();
  }

  private static void watercraft() {
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
    System.out.printf(
        "watercraft netFy=%.4f vy=%.4f dy=%.4f%n",
        netY, body.linearVelocity().y(), body.transform().position().y());
  }

  private static void airship() {
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
    double netY =
        gravity.apply(body, world).force().y() + aerostatic.apply(body, world).force().y();
    new PhysicsEngine().step(world, List.of(body));
    System.out.printf(
        "airship netFy=%.4f vy=%.4f restAirspeed=%.4f%n", netY, body.linearVelocity().y(), 0.0);
  }

  private static void airplane() {
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
    System.out.printf(
        "airplane restLiftY=%.4f speedLiftY=%.4f restVy=%.4f speedVy=%.4f%n",
        restLift, speedLift, rest.linearVelocity().y(), flying.linearVelocity().y());
  }
}
