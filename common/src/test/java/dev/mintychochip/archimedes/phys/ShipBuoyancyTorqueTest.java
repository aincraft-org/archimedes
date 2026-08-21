package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.phys.Aabb;
import dev.mintychochip.phys.BodyImpl;
import dev.mintychochip.phys.Collider;
import dev.mintychochip.phys.ColliderImpl;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.Material;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.World;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class ShipBuoyancyTorqueTest {
  @Test
  void offsetWetCellProducesRestoringTorqueAboutCom() {
    Force.Result result =
        new ShipBuoyancyForce().apply(twoCubeRod(), world(fluidWhenZNonNegative()));
    assertTrue(result.force().y() > 0);
    assertEquals(0.0, result.torque().y(), 1e-6);
    assertTrue(Math.abs(result.torque().x()) > 1e-6, "pitch torque from offset CoB");
    assertEquals(0.0, result.torque().z(), 1.0);
  }

  @Test
  void centeredCubeAppliesZeroTorqueWhenFullyWetOrDry() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            1,
            List.of(unitCube(new Vector3d())),
            List.of());
    Force.Result wet = new ShipBuoyancyForce().apply(body, world(everywhere(true, 1000.0)));
    Force.Result dry = new ShipBuoyancyForce().apply(body, world(everywhere(false, 1000.0)));
    assertTrue(wet.force().y() > 0);
    assertEquals(0.0, wet.torque().length(), 1e-9);
    assertEquals(0.0, dry.force().length(), 1e-9);
    assertEquals(0.0, dry.torque().length(), 1e-9);
  }

  private static BodyImpl twoCubeRod() {
    return new BodyImpl(
        new Transform(new Vector3d(), new Quaterniond()),
        2,
        List.of(unitCube(new Vector3d(0, 0, 2)), unitCube(new Vector3d(0, 0, -2))),
        List.of());
  }

  private static Collider unitCube(Vector3d local) {
    return new ColliderImpl(
        new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5)),
        new Material(1),
        new Transform(local, new Quaterniond()));
  }

  private static FluidField fluidWhenZNonNegative() {
    return new FluidField() {
      public boolean isFluid(Vector3dc point) {
        return point.z() >= 0;
      }

      public double density(Vector3dc point) {
        return 1000.0;
      }
    };
  }

  private static FluidField everywhere(boolean fluid, double density) {
    return new FluidField() {
      public boolean isFluid(Vector3dc point) {
        return fluid;
      }

      public double density(Vector3dc point) {
        return density;
      }
    };
  }

  private static World world(FluidField fluids) {
    Vector3d gravity = new Vector3d(0, -10, 0);
    return new World() {
      public Vector3dc gravity() {
        return gravity;
      }

      public FluidField fluidField() {
        return fluids;
      }

      public double timeStep() {
        return 0.1;
      }
    };
  }
}
