package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class QuadraticDragForceTest {
  @Test
  void restProducesZeroDrag() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new QuadraticDragForce(0.5).apply(body, world);

    assertEquals(0.0, result.force().length(), 0.0);
  }

  @Test
  void dragOpposesVelocityWithQuadraticMagnitude() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(3, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new QuadraticDragForce(2).apply(body, world);

    assertEquals(-18.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertTrue(result.force().x() < 0);
  }

  @Test
  void densityScaledDragIsZeroInVacuumAndStrongerInLiquid() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(2, 0, 0));
    World any = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    QuadraticDragForce air =
        new QuadraticDragForce(0.5, DensityField.liquid(PhysFixtures.vacuum()));
    QuadraticDragForce water =
        new QuadraticDragForce(0.5, DensityField.liquid(PhysFixtures.liquidBelow(10, 1000)));
    assertEquals(0.0, air.apply(body, any).force().length(), 0.0);
    assertEquals(-0.5 * 1000 * 2 * 2, water.apply(body, any).force().x(), 1e-6);
    assertTrue(water.densityScaled());
    assertFalse(new QuadraticDragForce(0.5).densityScaled());
  }

  @Test
  void densityDragAtRestIsZero() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new QuadraticDragForce(2, DensityField.uniform(1000)).apply(body, world);

    assertEquals(0.0, result.force().length(), 0.0);
    assertEquals(0.0, result.torque().length(), 0.0);
  }

  @Test
  void densityDragScalesLumpedLawBySampledDensity() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(3, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result water = new QuadraticDragForce(2, DensityField.uniform(1000)).apply(body, world);
    Force.Result air = new QuadraticDragForce(2, DensityField.uniform(1.2)).apply(body, world);

    assertEquals(-18000.0, water.force().x(), 1e-6);
    assertEquals(-21.6, air.force().x(), 1e-9);
    assertEquals(1000.0 / 1.2, water.force().x() / air.force().x(), 1e-9);
    assertEquals(0.0, water.torque().length(), 0.0);
  }

  @Test
  void densityDragUsesMeanColliderDensity() {
    FluidField water = PhysFixtures.liquidBelow(0, 1000);
    Collider hull = PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(hull), List.of());
    body.setLinearVelocity(new Vector3d(2, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), water);

    Force.Result result = new QuadraticDragForce(1, DensityField.liquid(water)).apply(body, world);

    // Half-submerged unit cube: mean ρ ≈ 500; F = −1 * 500 * 2² = −2000
    assertEquals(-2000.0, result.force().x(), 1.0);
  }

  @Test
  void driftingWithTheCurrentProducesZeroDrag() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(4, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    FlowField current = FlowField.uniform(new Vector3d(4, 0, 0));

    Force.Result result =
        new QuadraticDragForce(2, DensityField.uniform(1), current).apply(body, world);

    assertEquals(0.0, result.force().length(), 1e-12);
  }

  @Test
  void restInACurrentIsDraggedWithTheFlow() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    FlowField current = FlowField.uniform(new Vector3d(3, 0, 0));

    Force.Result result = new QuadraticDragForce(2, current).apply(body, world);

    assertEquals(18.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertEquals(0.0, result.force().z(), 1e-9);
  }

  @Test
  void relativeFlowDragAcceleratesAParkedBodyTowardTheCurrentOnStep() {
    FlowField current = FlowField.uniform(new Vector3d(5, 0, 0));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(),
            List.of(new QuadraticDragForce(0.2, current)));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(body));

    assertTrue(body.linearVelocity().x() > 0);
  }

  @Test
  void denserMediumBleedsSpeedFasterOnStep() {
    QuadraticDragForce waterDrag = new QuadraticDragForce(0.001, DensityField.uniform(1000));
    QuadraticDragForce airDrag = new QuadraticDragForce(0.001, DensityField.uniform(1.2));
    BodyImpl water =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(waterDrag));
    BodyImpl air =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of(airDrag));
    water.setLinearVelocity(new Vector3d(10, 0, 0));
    air.setLinearVelocity(new Vector3d(10, 0, 0));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(water, air));

    assertTrue(water.linearVelocity().x() < air.linearVelocity().x());
    assertTrue(air.linearVelocity().x() < 10);
    assertTrue(water.linearVelocity().x() > 0);
  }
}
