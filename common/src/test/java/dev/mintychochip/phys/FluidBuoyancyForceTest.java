package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class FluidBuoyancyForceTest {
  @Test
  void uniformMediumProducesDisplacedMassTimesMinusGravity() {
    Collider envelope = PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 1, List.of(envelope), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    Force.Result result = new FluidBuoyancyForce(DensityField.uniform(1.2)).apply(body, world);

    assertEquals(0.0, result.force().x(), 1e-9);
    assertEquals(12.0, result.force().y(), 1e-9);
    assertEquals(0.0, result.force().z(), 1e-9);
    assertEquals(0.0, result.torque().length(), 1e-9);
  }

  @Test
  void worldLiquidUsesOnlyIsFluidRegions() {
    FluidField water = PhysFixtures.liquidBelow(10, 1000);
    Collider envelope = PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(0, 0, 0), new Quaterniond()),
            500,
            List.of(envelope),
            List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), water);

    Force.Result submerged = new FluidBuoyancyForce().apply(body, world);
    body.setTransform(new Transform(new Vector3d(0, 20, 0), new Quaterniond()));
    Force.Result inAir = new FluidBuoyancyForce().apply(body, world);

    assertEquals(10000.0, submerged.force().y(), 1e-6);
    assertEquals(0.0, inAir.force().length(), 1e-9);
  }

  @Test
  void halfSubmergedBoxDisplacesAboutHalfTheLiquid() {
    FluidField water = PhysFixtures.liquidBelow(0, 1000);
    Collider envelope = PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 1, List.of(envelope), List.of());
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), water);

    Force.Result result = new FluidBuoyancyForce().apply(body, world);

    assertEquals(5000.0, result.force().y(), 1.0);
  }
}
