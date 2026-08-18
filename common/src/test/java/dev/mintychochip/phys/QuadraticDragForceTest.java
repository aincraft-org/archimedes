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
}
