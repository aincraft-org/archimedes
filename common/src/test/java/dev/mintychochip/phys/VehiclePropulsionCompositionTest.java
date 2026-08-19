package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

/**
 * Ships and airships share the shipped propulsion catalog. Vehicle type is the force list, not a
 * subclass. Pressure sails drive both; they cannot go upwind. Rocket thrust ignores density.
 */
class VehiclePropulsionCompositionTest {
  @Test
  void catalogHasSailsRocketsMediumThrustLiftingSailsAndKeel() throws ClassNotFoundException {
    Class.forName("dev.mintychochip.phys.PressureSailForce");
    Class.forName("dev.mintychochip.phys.ThrustForce");
    Class.forName("dev.mintychochip.phys.FluidBuoyancyForce");
    Class.forName("dev.mintychochip.phys.LiftForce");
    Class.forName("dev.mintychochip.phys.MediumThrustForce");
    Class.forName("dev.mintychochip.phys.LiftingSailForce");
    Class.forName("dev.mintychochip.phys.KeelForce");
    boolean densityDrag =
        Arrays.stream(QuadraticDragForce.class.getConstructors())
            .anyMatch(ctor -> Arrays.asList(ctor.getParameterTypes()).contains(DensityField.class));
    assertTrue(densityDrag, "density-scaled quadratic drag is shipped as an opt-in constructor");
  }

  @Test
  void samePressureSailDrivesAWatercraftAndAnAirshipDownwind() {
    FlowField wind = FlowField.uniform(new Vector3d(0, 0, 10));
    DensityField air = DensityField.uniform(1.2);
    PressureSailForce sail =
        new PressureSailForce(new Vector3d(), new Vector3d(0, 0, 1), 2, air, wind);
    GravityForce gravity = new GravityForce();
    FluidBuoyancyForce liquid = new FluidBuoyancyForce();
    FluidBuoyancyForce aerostatic = new FluidBuoyancyForce(air);

    BodyImpl watercraft =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            500,
            List.of(PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5))),
            List.of(gravity, liquid, sail));
    BodyImpl airship =
        new BodyImpl(
            new Transform(new Vector3d(0, 40, 0), new Quaterniond()),
            1000,
            List.of(PhysFixtures.box(new Vector3d(), new Vector3d(5, 5, 5))),
            List.of(gravity, aerostatic, sail));
    World world =
        PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.liquidBelow(10, 1000));

    assertFalse(world.fluidField().isFluid(airship.transform().position()));
    new PhysicsEngine().step(world, List.of(watercraft, airship));

    assertTrue(watercraft.linearVelocity().y() > 0, "displaced liquid still lifts the hull");
    assertTrue(watercraft.linearVelocity().z() > 0, "the same sail drives the hull downwind");
    assertTrue(airship.linearVelocity().y() > 0, "aerostatic lift holds the envelope in empty air");
    assertTrue(airship.linearVelocity().z() > 0, "the same sail drives the envelope downwind");
  }

  @Test
  void pressureSailNeverProducesAnUpwindForce() {
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    DensityField air = DensityField.uniform(1.2);
    FlowField fromEast = FlowField.uniform(new Vector3d(10, 0, 0));

    Force.Result downwind =
        new PressureSailForce(new Vector3d(), new Vector3d(1, 0, 0), 2, air, fromEast)
            .apply(body, world);
    Force.Result backToWind =
        new PressureSailForce(new Vector3d(), new Vector3d(-1, 0, 0), 2, air, fromEast)
            .apply(body, world);
    Force.Result edgeOn =
        new PressureSailForce(new Vector3d(), new Vector3d(0, 0, 1), 2, air, fromEast)
            .apply(body, world);

    assertTrue(downwind.force().x() > 0);
    assertEquals(0.0, backToWind.force().length(), 0.0);
    assertEquals(0.0, edgeOn.force().length(), 0.0);
    assertTrue(downwind.force().x() >= 0);
    assertTrue(backToWind.force().x() >= 0);
    assertTrue(edgeOn.force().x() >= 0);
  }

  @Test
  void rocketThrustIsIdenticalInVacuumAndInAir() {
    ThrustForce rocket = new ThrustForce(new Vector3d(0, 0, 1), 12);
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World vacuum = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    World air =
        PhysFixtures.world(
            0.1,
            new Vector3d(0, -10, 0),
            new FluidField() {
              public boolean isFluid(Vector3dc point) {
                return false;
              }

              public double density(Vector3dc point) {
                return 1.2;
              }
            });

    Force.Result inVacuum = rocket.apply(body, vacuum);
    Force.Result inAir = rocket.apply(body, air);

    assertEquals(inVacuum.force().x(), inAir.force().x(), 0.0);
    assertEquals(inVacuum.force().y(), inAir.force().y(), 0.0);
    assertEquals(inVacuum.force().z(), inAir.force().z(), 0.0);
    assertEquals(12.0, inAir.force().z(), 0.0);
  }
}
