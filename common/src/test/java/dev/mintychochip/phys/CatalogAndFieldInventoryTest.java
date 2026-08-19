package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.phys.EnvelopeBuoyancyForce;
import dev.mintychochip.archimedes.phys.ShipBuoyancyForce;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Structural inventory of shipped {@link Force} types and environmental samplers. Proves the
 * accuracy-gap list against real classes, not a markdown dump.
 */
class CatalogAndFieldInventoryTest {
  @Test
  void everyShippedForceImplementationIsLoadable() throws ClassNotFoundException {
    for (String type :
        List.of(
            "dev.mintychochip.phys.GravityForce",
            "dev.mintychochip.phys.FluidBuoyancyForce",
            "dev.mintychochip.phys.QuadraticDragForce",
            "dev.mintychochip.phys.ThrustForce",
            "dev.mintychochip.phys.MediumThrustForce",
            "dev.mintychochip.phys.LiftForce",
            "dev.mintychochip.phys.PressureSailForce",
            "dev.mintychochip.phys.SupportForce",
            "dev.mintychochip.phys.CoulombFrictionForce",
            "dev.mintychochip.phys.ViscousDragForce",
            "dev.mintychochip.phys.AngularDragForce",
            "dev.mintychochip.phys.VegetationDragForce",
            "dev.mintychochip.archimedes.phys.ShipBuoyancyForce",
            "dev.mintychochip.archimedes.phys.EnvelopeBuoyancyForce")) {
      assertTrue(Force.class.isAssignableFrom(Class.forName(type)), type);
    }
  }

  @Test
  void worldHasNoDensityOrFlowOrCurrentSampler() {
    Set<String> names = new HashSet<>();
    for (Method method : World.class.getMethods()) {
      names.add(method.getName());
    }
    assertTrue(names.contains("gravity"));
    assertTrue(names.contains("fluidField"));
    assertTrue(names.contains("timeStep"));
    assertTrue(names.contains("isObstacle"));
    assertTrue(names.contains("isChunkLoaded"));
    assertTrue(names.contains("vegetation"));
    assertFalse(names.contains("densityField"));
    assertFalse(names.contains("flowField"));
    assertFalse(names.contains("current"));
    assertFalse(names.contains("velocity"));
  }

  @Test
  void fluidFieldHasNoMediumVelocity() {
    Set<String> names = new HashSet<>();
    for (Method method : FluidField.class.getMethods()) {
      names.add(method.getName());
    }
    assertTrue(names.contains("isFluid"));
    assertTrue(names.contains("density"));
    assertFalse(names.contains("velocity"));
  }

  @Test
  void quadraticDragConstructorsTakeOptionalFlow() {
    boolean sawLumped = false;
    boolean sawDensity = false;
    boolean sawFlow = false;
    for (Constructor<?> ctor : QuadraticDragForce.class.getConstructors()) {
      List<Class<?>> params = Arrays.asList(ctor.getParameterTypes());
      if (params.equals(List.of(double.class))) {
        sawLumped = true;
      }
      if (params.contains(DensityField.class)) {
        sawDensity = true;
      }
      if (params.contains(FlowField.class)) {
        sawFlow = true;
      }
    }
    assertTrue(sawLumped);
    assertTrue(sawDensity);
    assertTrue(sawFlow);
  }

  @Test
  void quadraticDragOpposesBodyVelocityNotInjectedFlow() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(3, 0, 0));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    Force.Result result = new QuadraticDragForce(2).apply(body, world);
    assertEquals(-18.0, result.force().x(), 1e-9);
    assertEquals(0.0, result.force().y(), 1e-9);
    assertEquals(0.0, result.force().z(), 1e-9);
  }

  @Test
  void bodyInertiaIsIsotropicMass() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 7, List.of(), List.of());
    Matrix3dc inertia = body.inertia();
    assertEquals(7.0, inertia.m00(), 1e-12);
    assertEquals(7.0, inertia.m11(), 1e-12);
    assertEquals(7.0, inertia.m22(), 1e-12);
    assertEquals(0.0, inertia.m01(), 1e-12);
    assertEquals(0.0, inertia.m02(), 1e-12);
    assertEquals(0.0, inertia.m12(), 1e-12);
  }

  @Test
  void waterlineAndEnvelopeBuoyancyApplyZeroTorque() {
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            10,
            List.of(PhysFixtures.box(new Vector3d(), new Vector3d(0.5, 0.5, 0.5))),
            List.of());
    World dry = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    Force.Result waterline = new ShipBuoyancyForce().apply(body, dry);
    Force.Result envelope =
        new EnvelopeBuoyancyForce(2.0, DensityField.uniform(1.2)).apply(body, dry);
    assertEquals(0.0, waterline.torque().length(), 1e-12);
    assertEquals(0.0, envelope.torque().length(), 1e-12);
    assertNotEquals(0.0, envelope.force().length());
  }

  @Test
  void flowFieldExistsAsAConstructorInjectedSampler() {
    FlowField wind = FlowField.uniform(new Vector3d(0, 0, 8));
    assertEquals(8.0, wind.velocity(new Vector3d()).z(), 1e-12);
  }
}
