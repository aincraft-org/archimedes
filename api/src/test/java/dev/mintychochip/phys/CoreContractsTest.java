package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class CoreContractsTest {
  @Test
  void shapeAndFluidFieldUseAnonymousClasses() {
    Shape shape =
        new Shape() {
          public Bounds bounds(Transform transform) {
            return new Bounds() {
              public Vector3d min() {
                return new Vector3d(0, 0, 0);
              }

              public Vector3d max() {
                return new Vector3d(1, 1, 1);
              }

              public double volume() {
                return 1;
              }

              public boolean contains(Vector3dc point) {
                return point.x() >= 0 && point.x() <= 1;
              }
            };
          }

          public double volume() {
            return 1;
          }
        };
    FluidField fluids =
        new FluidField() {
          public boolean isFluid(Vector3dc point) {
            return point.y() < 0;
          }

          public double density(Vector3dc point) {
            return 1000;
          }
        };
    assertEquals(1, shape.volume());
    assertTrue(fluids.isFluid(new Vector3d(0, -1, 0)));
  }

  @Test
  void forceResultIsTyped() {
    Force.Result result = new Force.Result(new Vector3d(1, 2, 3), new Vector3d(0, 0, 0));
    assertEquals(2, result.force().y());
  }
}
