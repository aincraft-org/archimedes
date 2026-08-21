package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class MediumThrustForceTest {
  @Test
  void vacuumProducesZeroForceAndTorque() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    MediumThrustForce thrust =
        new MediumThrustForce(
            new Vector3d(0, 0, 1), new Vector3d(1, 0, 0), 2, DensityField.uniform(0));

    Force.Result result = thrust.apply(body, world);

    assertEquals(0.0, result.force().length(), 0.0);
    assertEquals(0.0, result.torque().length(), 0.0);
  }

  @Test
  void forceScalesWithSampledDensity() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    Vector3d point = new Vector3d();
    Vector3d axis = new Vector3d(1, 0, 0);
    Force.Result water =
        new MediumThrustForce(point, axis, 2, DensityField.uniform(1000)).apply(body, world);
    Force.Result air =
        new MediumThrustForce(point, axis, 2, DensityField.uniform(1.2)).apply(body, world);

    assertEquals(2000.0, water.force().x(), 1e-9);
    assertEquals(2.4, air.force().x(), 1e-9);
    assertEquals(1000.0 / 1.2, water.force().x() / air.force().x(), 1e-9);
    assertEquals(0.0, water.torque().length(), 0.0);
  }

  @Test
  void offsetPointProducesRCrossFTorque() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result =
        new MediumThrustForce(
                new Vector3d(0, 0, 1), new Vector3d(1, 0, 0), 1, DensityField.uniform(1000))
            .apply(body, world);

    assertEquals(1000.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertEquals(0.0, result.force().z(), 1e-9);
    assertEquals(0.0, result.torque().x(), 1e-9);
    assertEquals(1000.0, result.torque().y(), 1e-9);
    assertEquals(0.0, result.torque().z(), 1e-9);
  }

  @Test
  void rotatesWithBodyOrientation() {
    Quaterniond yaw = new Quaterniond().rotateY(Math.PI / 2);
    BodyImpl body = new BodyImpl(new Transform(new Vector3d(), yaw), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result =
        new MediumThrustForce(new Vector3d(), new Vector3d(1, 0, 0), 10, DensityField.uniform(1))
            .apply(body, world);

    assertEquals(0.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertEquals(-10.0, result.force().z(), 1e-9);
  }

  @Test
  void defaultConstructorUsesWorldLiquidOnly() {
    FluidField water = PhysFixtures.liquidBelow(10, 1000);
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World wet = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), water);
    World dry = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    MediumThrustForce thrust = new MediumThrustForce(new Vector3d(), new Vector3d(0, 0, 1), 1);

    assertEquals(1000.0, thrust.apply(body, wet).force().z(), 1e-9);
    assertEquals(0.0, thrust.apply(body, dry).force().length(), 0.0);
  }

  @Test
  void rejectsZeroAxisAndNegativeCoefficient() {
    Vector3d point = new Vector3d();
    assertThrows(
        IllegalArgumentException.class,
        () -> new MediumThrustForce(point, new Vector3d(), 1, DensityField.uniform(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MediumThrustForce(point, new Vector3d(1, 0, 0), -1, DensityField.uniform(1)));
  }

  @Test
  void vacuumStepLeavesVelocityUnchanged() {
    MediumThrustForce thrust =
        new MediumThrustForce(new Vector3d(), new Vector3d(1, 0, 0), 8, DensityField.uniform(0));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of(thrust));
    World world = PhysFixtures.world(0.2, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(0.0, body.linearVelocity().length(), 0.0);
    assertEquals(0.0, body.angularVelocity().length(), 0.0);
  }

  @Test
  void offsetStepProducesForwardSpeedAndSpin() {
    MediumThrustForce thrust =
        new MediumThrustForce(
            new Vector3d(0, 0, 1), new Vector3d(1, 0, 0), 1, DensityField.uniform(1000));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of(thrust));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(50.0, body.linearVelocity().x(), 1e-9);
    assertEquals(50.0, body.angularVelocity().y(), 1e-9);
    assertTrue(body.transform().position().x() > 0);
  }

  @Test
  void thrustAtOffsetFromCenterOfMassProducesTorque() {
    // collider at origin so CoM=0, localPoint=(0,1,0), F along +Z
    // τ = (0,1,0)×(0,0,F) = (F,0,0)
    Collider box = PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    MediumThrustForce thrust =
        new MediumThrustForce(
            new Vector3d(0, 1, 0), new Vector3d(0, 0, 1), 1, DensityField.uniform(1));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(box), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(), PhysFixtures.vacuum());
    Force.Result result = thrust.apply(body, world);
    assertEquals(0.0, result.torque().y(), 1e-9);
    assertEquals(0.0, result.torque().z(), 1e-9);
    assertEquals(result.force().z(), result.torque().x(), 1e-9);
    assertTrue(Math.abs(result.torque().x()) > 0);
  }
}
