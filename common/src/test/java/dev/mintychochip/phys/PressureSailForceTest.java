package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class PressureSailForceTest {
  @Test
  void stillAirAtRestProducesNoForce() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PressureSailForce sail =
        new PressureSailForce(
            new Vector3d(), new Vector3d(1, 0, 0), 2, DensityField.uniform(1.2), FlowField.still());

    Force.Result result = sail.apply(body, world);

    assertEquals(0.0, result.force().length(), 0.0);
    assertEquals(0.0, result.torque().length(), 0.0);
  }

  @Test
  void vacuumProducesNoForceEvenInWind() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PressureSailForce sail =
        new PressureSailForce(
            new Vector3d(),
            new Vector3d(1, 0, 0),
            2,
            DensityField.uniform(0),
            FlowField.uniform(new Vector3d(10, 0, 0)));

    assertEquals(0.0, sail.apply(body, world).force().length(), 0.0);
  }

  @Test
  void restInBreezePushesAlongTheSailNormal() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PressureSailForce sail =
        new PressureSailForce(
            new Vector3d(),
            new Vector3d(1, 0, 0),
            2,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(10, 0, 0)));

    Force.Result result = sail.apply(body, world);

    assertEquals(120.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertEquals(0.0, result.force().z(), 1e-9);
    assertEquals(0.0, result.torque().length(), 0.0);
  }

  @Test
  void edgeOnSheetKillsTheForce() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PressureSailForce sail =
        new PressureSailForce(
            new Vector3d(),
            new Vector3d(0, 1, 0),
            2,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(10, 0, 0)));

    assertEquals(0.0, sail.apply(body, world).force().length(), 1e-9);
  }

  @Test
  void windOnTheBackProducesNoForce() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PressureSailForce sail =
        new PressureSailForce(
            new Vector3d(),
            new Vector3d(-1, 0, 0),
            2,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(10, 0, 0)));

    assertEquals(0.0, sail.apply(body, world).force().length(), 1e-9);
  }

  @Test
  void offsetSailProducesYawTorque() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PressureSailForce sail =
        new PressureSailForce(
            new Vector3d(0, 0, 1),
            new Vector3d(1, 0, 0),
            2,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(10, 0, 0)));

    Force.Result result = sail.apply(body, world);

    assertEquals(120.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.torque().x(), 1e-9);
    assertEquals(120.0, result.torque().y(), 1e-9);
    assertEquals(0.0, result.torque().z(), 1e-9);
  }

  @Test
  void spinCreatesApparentWindInStillAir() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setAngularVelocity(new Vector3d(0, 1, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PressureSailForce sail =
        new PressureSailForce(
            new Vector3d(1, 0, 0),
            new Vector3d(0, 0, 1),
            2,
            DensityField.uniform(1.2),
            FlowField.still());

    assertTrue(sail.apply(body, world).force().length() > 0);
  }

  @Test
  void restInBreezeStartsMovingAlongTheNormal() {
    PressureSailForce sail =
        new PressureSailForce(
            new Vector3d(),
            new Vector3d(1, 0, 0),
            2,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(10, 0, 0)));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of(sail));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertEquals(6.0, body.linearVelocity().x(), 1e-9);
    assertTrue(body.transform().position().x() > 0);
  }

  @Test
  void rejectsZeroAreaAndZeroNormal() {
    DensityField air = DensityField.uniform(1.2);
    FlowField wind = FlowField.still();
    assertThrows(
        IllegalArgumentException.class,
        () -> new PressureSailForce(new Vector3d(), new Vector3d(), 1, air, wind));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PressureSailForce(new Vector3d(), new Vector3d(1, 0, 0), 0, air, wind));
  }
}
