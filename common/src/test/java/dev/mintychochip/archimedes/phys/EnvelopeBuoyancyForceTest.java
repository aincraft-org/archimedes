package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.phys.BodyImpl;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.GravityForce;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.World;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class EnvelopeBuoyancyForceTest {
  @Test
  void liftUsesEnvelopeVolumeOnly() {
    EnvelopeBuoyancyForce force = new EnvelopeBuoyancyForce(2.0, DensityField.uniform(1.2));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 10, List.of(), List.of());
    Force.Result result = force.apply(body, new UpWorld());
    assertEquals(0, result.force().x(), 1e-9);
    assertEquals(24.0, result.force().y(), 1e-9); // 2 * 1.2 * 10
    assertEquals(0, result.force().z(), 1e-9);
    assertEquals(0, result.torque().length(), 1e-9);
  }

  @Test
  void zeroVolumeIsZeroForce() {
    EnvelopeBuoyancyForce force = new EnvelopeBuoyancyForce(0, DensityField.uniform(1.2));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 10, List.of(), List.of());
    Force.Result result = force.apply(body, new UpWorld());
    assertEquals(0, result.force().length(), 1e-9);
  }

  @Test
  void stepInVacuumProducesNetUp() {
    // ρ V = 12 > body mass 10, so aerostatic lift exceeds weight.
    EnvelopeBuoyancyForce lift = new EnvelopeBuoyancyForce(10.0, DensityField.uniform(1.2));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(0, 40, 0), new Quaterniond()),
            10,
            List.of(),
            List.of(new GravityForce(), lift));
    new PhysicsEngine().step(new UpWorld(), List.of(body));
    assertTrue(body.linearVelocity().y() > 0);
  }

  @Test
  void rejectsNegativeAndNonFiniteVolume() {
    DensityField air = DensityField.uniform(1.2);
    assertThrows(IllegalArgumentException.class, () -> new EnvelopeBuoyancyForce(-1, air));
    assertThrows(IllegalArgumentException.class, () -> new EnvelopeBuoyancyForce(Double.NaN, air));
    assertThrows(
        IllegalArgumentException.class,
        () -> new EnvelopeBuoyancyForce(Double.POSITIVE_INFINITY, air));
  }

  @Test
  void rejectsNullAir() {
    assertThrows(NullPointerException.class, () -> new EnvelopeBuoyancyForce(1, null));
  }

  static final class UpWorld implements World {
    public Vector3dc gravity() {
      return new Vector3d(0, -10, 0);
    }

    public FluidField fluidField() {
      return new FluidField() {
        public boolean isFluid(Vector3dc point) {
          return false;
        }

        public double density(Vector3dc point) {
          return 0;
        }
      };
    }

    public double timeStep() {
      return 0.05;
    }
  }
}
