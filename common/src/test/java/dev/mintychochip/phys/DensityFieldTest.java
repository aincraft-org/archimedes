package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class DensityFieldTest {
  @Test
  void liquidIgnoresDensityWhenIsFluidIsFalse() {
    FluidField airLookingLikeWater =
        new FluidField() {
          public boolean isFluid(Vector3dc point) {
            return false;
          }

          public double density(Vector3dc point) {
            return 1.2;
          }
        };

    DensityField liquid = DensityField.liquid(airLookingLikeWater);

    assertEquals(0.0, liquid.density(new Vector3d(0, 10, 0)), 0.0);
  }

  @Test
  void liquidUsesFluidDensityOnlyWhereIsFluid() {
    FluidField water = PhysFixtures.liquidBelow(0, 1000);

    DensityField liquid = DensityField.liquid(water);

    assertEquals(1000.0, liquid.density(new Vector3d(0, -1, 0)), 0.0);
    assertEquals(0.0, liquid.density(new Vector3d(0, 1, 0)), 0.0);
  }

  @Test
  void uniformReturnsTheSameDensityEverywhere() {
    DensityField air = DensityField.uniform(1.2);

    assertEquals(1.2, air.density(new Vector3d(0, 100, 0)), 0.0);
    assertEquals(1.2, air.density(new Vector3d(0, -50, 0)), 0.0);
  }
}
