# Medium-Coupled Thrust and Drag Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `MediumThrustForce` and an opt-in density-scaled `QuadraticDragForce` overload so watercraft and later airships share the same catalog forces.

**Architecture:** Keep `ThrustForce` as the density-blind rocket. New `MediumThrustForce` samples `DensityField` at a rotated body-local point and returns `r × F` torque. `QuadraticDragForce(c)` stays lumped; `QuadraticDragForce(c, DensityField)` multiplies by volume-weighted mean density. Extract `FluidBuoyancyForce`'s grid into package-private `DensitySampling` so buoyancy and density drag cannot drift. No `World.densityField()`, no ship wiring.

**Tech Stack:** Java 25, Gradle (`:common` tests), JUnit 5, JOML, existing `dev.mintychochip.phys` catalog. Quality gate `./gradlew check`.

**Spec:** `docs/superpowers/specs/2026-08-17-medium-propulsion-design.md`

---

## File Map

### Create

- `common/src/main/java/dev/mintychochip/phys/MediumThrustForce.java` — density-scaled actuator
- `common/src/main/java/dev/mintychochip/phys/DensitySampling.java` — package-private grid used by buoyancy and density drag
- `common/src/test/java/dev/mintychochip/phys/MediumThrustForceTest.java` — apply + step signatures

### Modify

- `common/src/main/java/dev/mintychochip/phys/FluidBuoyancyForce.java` — call `DensitySampling.displacedMass`
- `common/src/main/java/dev/mintychochip/phys/QuadraticDragForce.java` — optional `DensityField` constructor
- `common/src/test/java/dev/mintychochip/phys/QuadraticDragForceTest.java` — density apply + step cases
- `common/src/test/java/dev/mintychochip/phys/CatalogStepTest.java` — catalog `step` signatures
- `docs/specs/physics.md` — check off Next items after verification

### Do not touch

- `ThrustForce`, `LiftForce`, `ViscousDragForce`, `AngularDragForce`
- `World`, `DensityField` API (no `World.densityField()`)
- Any `dev.mintychochip.archimedes.*` ship gameplay type
- `ShipPose`, commands, `ShipRuntime`

## Global Constraints

- `MediumThrustForce` no-medium constructor uses `DensityField.liquid(world.fluidField())` at apply time. Never read `isFluid` except through that adapter.
- `QuadraticDragForce(double)` must keep `F = −c |v| v`. Existing test `v=(3,0,0), c=2` ⇒ `F_x = −18` stays green.
- `QuadraticDragForce(double, DensityField)` requires a non-null field. Null does not mean liquid.
- Forces are immutable. Coefficient is throttle.
- Every new law needs both `apply` and `Physics.step` assertions.
- Match existing force style: `final` class, field javadocs, constructor javadocs, `Vectors.requireFinite`, JOML `Vector3d`.
- `./gradlew :common:test --tests <class>` for task loops; `./gradlew check` before the last commit.

---

### Task 1: MediumThrustForce

**Files:**
- Create: `common/src/test/java/dev/mintychochip/phys/MediumThrustForceTest.java`
- Create: `common/src/main/java/dev/mintychochip/phys/MediumThrustForce.java`

- [ ] **Step 1: Write the failing apply and step tests**

```java
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
        new MediumThrustForce(new Vector3d(0, 0, 1), new Vector3d(1, 0, 0), 2, DensityField.uniform(0));

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
        new MediumThrustForce(new Vector3d(0, 0, 1), new Vector3d(1, 0, 0), 1, DensityField.uniform(1000))
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.MediumThrustForceTest`

Expected: FAIL to compile (`MediumThrustForce` cannot be resolved).

- [ ] **Step 3: Implement `MediumThrustForce`**

```java
package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Density-scaled thrust at a body-local point: {@code F = k ρ n̂}, {@code τ = r × F}. */
public final class MediumThrustForce implements Force {
  /** Application point in the body frame. */
  private final Vector3d localPoint;

  /** Unit thrust axis in the body frame. */
  private final Vector3d localAxis;

  /** Thrust coefficient {@code k}. */
  private final double coefficient;

  /** Null means world's liquid at apply time. */
  private final DensityField medium;

  /**
   * World-liquid actuator. Samples {@link DensityField#liquid(FluidField)} at the application point.
   *
   * @param localPoint body-frame application point
   * @param localAxis body-frame thrust axis (normalized on store)
   * @param coefficient non-negative {@code k}
   */
  public MediumThrustForce(Vector3dc localPoint, Vector3dc localAxis, double coefficient) {
    this(localPoint, localAxis, coefficient, null, false);
  }

  /**
   * Actuator that samples an explicit medium at the application point.
   *
   * @param localPoint body-frame application point
   * @param localAxis body-frame thrust axis (normalized on store)
   * @param coefficient non-negative {@code k}
   * @param medium density sampler
   */
  public MediumThrustForce(
      Vector3dc localPoint, Vector3dc localAxis, double coefficient, DensityField medium) {
    this(localPoint, localAxis, coefficient, Objects.requireNonNull(medium), false);
  }

  private MediumThrustForce(
      Vector3dc localPoint,
      Vector3dc localAxis,
      double coefficient,
      DensityField medium,
      boolean ignored) {
    Objects.requireNonNull(localPoint);
    Objects.requireNonNull(localAxis);
    Vectors.requireFinite(localPoint);
    Vectors.requireFinite(localAxis);
    if (localAxis.lengthSquared() == 0) {
      throw new IllegalArgumentException("axis must be non-zero");
    }
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.localPoint = new Vector3d(localPoint);
    this.localAxis = new Vector3d(localAxis).normalize();
    this.coefficient = coefficient;
    this.medium = medium;
  }

  /**
   * Applies density-scaled thrust at the rotated application point.
   *
   * @param body body whose pose is sampled
   * @param world world supplying the default liquid field when no medium was given
   * @return force along the world axis and {@code r × F} torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    DensityField field = medium != null ? medium : DensityField.liquid(world.fluidField());
    Vector3d worldPoint =
        body.transform().orientation().transform(localPoint, new Vector3d());
    worldPoint.add(body.transform().position());
    double density = field.density(worldPoint);
    Vector3d force =
        body.transform().orientation().transform(localAxis, new Vector3d()).mul(coefficient * density);
    Vector3d radius = new Vector3d(worldPoint).sub(body.transform().position());
    return new Result(force, radius.cross(force, new Vector3d()));
  }
}
```

Checkstyle rejects unused parameters. If the private five-arg constructor's `ignored` flag trips PMD/Checkstyle, overload with a package-private nest instead: keep the two public constructors, duplicate the validation block in each, and assign `this.medium = null` vs `Objects.requireNonNull(medium)`. Prefer duplication of the ten validation lines over an unused flag if the quality gate complains.

Offset-step numbers: `F = 1000`, `m = 2`, `dt = 0.1` ⇒ `v_x = 50`. `τ_y = 1000`, `I = 2`, `I⁻¹ = 0.5` ⇒ `ω_y = 50`. `BodyImpl` inertia is `mass` on the diagonal.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.MediumThrustForceTest`

Expected: BUILD SUCCESSFUL, tests pass.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/phys/MediumThrustForce.java \
        common/src/test/java/dev/mintychochip/phys/MediumThrustForceTest.java
git commit -m "feat: add density-scaled medium thrust force"
```

---

### Task 2: Shared density sampling

**Files:**
- Create: `common/src/main/java/dev/mintychochip/phys/DensitySampling.java`
- Modify: `common/src/main/java/dev/mintychochip/phys/FluidBuoyancyForce.java`

- [ ] **Step 1: Run existing buoyancy tests as the characterization baseline**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.FluidBuoyancyForceTest --tests dev.mintychochip.phys.VehicleCompositionTest --tests dev.mintychochip.phys.CatalogStepTest`

Expected: BUILD SUCCESSFUL. Do not change assertions.

- [ ] **Step 2: Extract `DensitySampling` and switch buoyancy to it**

Create `common/src/main/java/dev/mintychochip/phys/DensitySampling.java`:

```java
package dev.mintychochip.phys;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Volume-grid density samples shared by hydrostatic buoyancy and density-scaled drag. */
final class DensitySampling {
  /** Prevents instantiation. */
  private DensitySampling() {}

  /**
   * Displaced fluid mass of one collider under {@code field}.
   *
   * @param body body providing the world translation
   * @param collider collider whose volume is sampled
   * @param field density sampler
   * @return finite non-negative mass
   */
  static double displacedMass(Body body, Collider collider, DensityField field) {
    double volume = collider.shape().volume();
    if (volume <= 0) {
      return 0;
    }
    Vector3d worldCenter =
        body.transform().position().add(collider.localTransform().position(), new Vector3d());
    Bounds bounds =
        collider
            .shape()
            .bounds(new Transform(worldCenter, collider.localTransform().orientation()));
    Vector3dc min = bounds.min();
    Vector3dc max = bounds.max();
    double sx = max.x() - min.x();
    double sy = max.y() - min.y();
    double sz = max.z() - min.z();
    int nx = sampleCount(sx);
    int ny = sampleCount(sy);
    int nz = sampleCount(sz);
    double cellVolume = volume / (nx * ny * nz);
    double mass = 0;
    for (int ix = 0; ix < nx; ix++) {
      for (int iy = 0; iy < ny; iy++) {
        for (int iz = 0; iz < nz; iz++) {
          Vector3d sample =
              new Vector3d(
                  min.x() + (ix + 0.5) * (sx / nx),
                  min.y() + (iy + 0.5) * (sy / ny),
                  min.z() + (iz + 0.5) * (sz / nz));
          mass += field.density(sample) * cellVolume;
        }
      }
    }
    return mass;
  }

  /**
   * Volume-weighted mean density. Bodies with no positive volume sample the body position.
   *
   * @param body body whose colliders are sampled
   * @param field density sampler
   * @return mean density
   */
  static double meanDensity(Body body, DensityField field) {
    double mass = 0;
    double volume = 0;
    for (Collider collider : body.colliders()) {
      double part = collider.shape().volume();
      if (part <= 0) {
        continue;
      }
      mass += displacedMass(body, collider, field);
      volume += part;
    }
    if (volume <= 0) {
      return field.density(body.transform().position());
    }
    return mass / volume;
  }

  private static int sampleCount(double extent) {
    if (extent <= 0) {
      return 1;
    }
    return Math.max(2, (int) Math.ceil(extent));
  }
}
```

Replace the private helpers in `FluidBuoyancyForce` so `apply` becomes:

```java
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    DensityField field = medium != null ? medium : DensityField.liquid(world.fluidField());
    double displacedMass = 0;
    for (Collider collider : body.colliders()) {
      displacedMass += DensitySampling.displacedMass(body, collider, field);
    }
    return new Result(new Vector3d(world.gravity()).mul(-displacedMass), new Vector3d());
  }
```

Delete `displacedMass` and `sampleCount` from `FluidBuoyancyForce`. Leave constructors and the `medium` field unchanged.

- [ ] **Step 3: Re-run the buoyancy characterization tests**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.FluidBuoyancyForceTest --tests dev.mintychochip.phys.VehicleCompositionTest --tests dev.mintychochip.phys.CatalogStepTest`

Expected: BUILD SUCCESSFUL with the same assertions (half-submerged box still `F_y ≈ 5000`).

- [ ] **Step 4: Commit**

```bash
git add common/src/main/java/dev/mintychochip/phys/DensitySampling.java \
        common/src/main/java/dev/mintychochip/phys/FluidBuoyancyForce.java
git commit -m "refactor: share collider density sampling"
```

---

### Task 3: Density-scaled quadratic drag

**Files:**
- Modify: `common/src/test/java/dev/mintychochip/phys/QuadraticDragForceTest.java`
- Modify: `common/src/main/java/dev/mintychochip/phys/QuadraticDragForce.java`

- [ ] **Step 1: Add failing density-drag tests to `QuadraticDragForceTest`**

Keep the two existing tests. Append:

```java
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

    Force.Result result =
        new QuadraticDragForce(1, DensityField.liquid(water)).apply(body, world);

    // Half-submerged unit cube: mean ρ ≈ 500; F = −1 * 500 * 2² = −2000
    assertEquals(-2000.0, result.force().x(), 1.0);
  }

  @Test
  void denserMediumBleedsSpeedFasterOnStep() {
    QuadraticDragForce waterDrag = new QuadraticDragForce(0.01, DensityField.uniform(1000));
    QuadraticDragForce airDrag = new QuadraticDragForce(0.01, DensityField.uniform(1.2));
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
```

Lumped numbers: no-collider body + `DensityField.uniform(1000)` ⇒ `ρ = 1000`. Existing lumped law at `c=2, v=3` is `−18`, so density law is `−18 * 1000 = −18000`.

Half-submerged: same grid as `FluidBuoyancyForceTest.halfSubmergedBoxDisplacesAboutHalfTheLiquid` (`F_y = 5000` with `g = −10` ⇒ displaced mass `500`, volume `1` ⇒ mean `ρ = 500`). Tolerance `1.0` matches that test.

- [ ] **Step 2: Run tests to verify the new ones fail**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.QuadraticDragForceTest`

Expected: FAIL to compile (`constructor QuadraticDragForce(double, DensityField)` not found). Existing two tests are unchanged.

- [ ] **Step 3: Implement the density overload**

Replace `common/src/main/java/dev/mintychochip/phys/QuadraticDragForce.java` with:

```java
package dev.mintychochip.phys;

import java.util.Objects;
import org.joml.Vector3d;

/** Quadratic drag opposing linear velocity: {@code F = −c |v| v}, optionally times {@code ρ}. */
public final class QuadraticDragForce implements Force {
  /** Drag coefficient {@code c}. */
  private final double coefficient;

  /** Null means lumped {@code F = −c |v| v}; non-null multiplies by sampled density. */
  private final DensityField medium;

  /**
   * Lumped quadratic drag. Does not sample a {@link DensityField}.
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   */
  public QuadraticDragForce(double coefficient) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
    this.medium = null;
  }

  /**
   * Density-scaled quadratic drag: {@code F = −c ρ |v| v}.
   *
   * @param coefficient non-negative quadratic coefficient {@code c}
   * @param medium density sampler; required
   */
  public QuadraticDragForce(double coefficient, DensityField medium) {
    if (!Double.isFinite(coefficient) || coefficient < 0) {
      throw new IllegalArgumentException("coefficient must be finite and non-negative");
    }
    this.coefficient = coefficient;
    this.medium = Objects.requireNonNull(medium);
  }

  /**
   * Applies drag opposing linear velocity with magnitude quadratic in speed.
   *
   * @param body body whose linear velocity is sampled
   * @param world world context; required for the force contract
   * @return quadratic drag force and zero torque
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    Vector3d velocity = new Vector3d(body.linearVelocity());
    double speed = velocity.length();
    if (speed == 0) {
      return new Result(new Vector3d(), new Vector3d());
    }
    double scale = coefficient;
    if (medium != null) {
      scale *= DensitySampling.meanDensity(body, medium);
    }
    return new Result(velocity.mul(-scale * speed), new Vector3d());
  }
}
```

Do not change the one-arg constructor's law. Do not treat a null medium as world liquid.

- [ ] **Step 4: Run drag tests**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.QuadraticDragForceTest --tests dev.mintychochip.phys.CatalogStepTest.quadraticDragStepReducesSpeedAndDiffersFromViscous --tests dev.mintychochip.phys.ViscousDragForceTest`

Expected: BUILD SUCCESSFUL. Lumped `F_x = −18` still holds.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/phys/QuadraticDragForce.java \
        common/src/test/java/dev/mintychochip/phys/QuadraticDragForceTest.java
git commit -m "feat: scale quadratic drag by sampled density"
```

---

### Task 4: Catalog composition and living spec

**Files:**
- Modify: `common/src/test/java/dev/mintychochip/phys/CatalogStepTest.java`
- Modify: `docs/specs/physics.md`

- [ ] **Step 1: Add failing composition step tests**

Append to `CatalogStepTest`:

```java
  @Test
  void mediumThrustStepIsZeroInVacuumAndSpinsWhenOffset() {
    MediumThrustForce dry =
        new MediumThrustForce(new Vector3d(), new Vector3d(1, 0, 0), 8, DensityField.uniform(0));
    MediumThrustForce offset =
        new MediumThrustForce(
            new Vector3d(0, 0, 1), new Vector3d(1, 0, 0), 1, DensityField.uniform(1000));
    BodyImpl vacuum =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of(dry));
    BodyImpl spinner =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of(offset));
    World world = PhysFixtures.world(0.1, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(vacuum, spinner));

    assertEquals(0.0, vacuum.linearVelocity().length(), 0.0);
    assertTrue(spinner.linearVelocity().x() > 0);
    assertTrue(spinner.angularVelocity().y() > 0);
  }

  @Test
  void mediumThrustPlusDensityDragIsSlowerThanThrustAlone() {
    MediumThrustForce thrust =
        new MediumThrustForce(new Vector3d(), new Vector3d(1, 0, 0), 4, DensityField.uniform(1000));
    QuadraticDragForce drag = new QuadraticDragForce(0.02, DensityField.uniform(1000));
    BodyImpl driven =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), List.of(thrust));
    BodyImpl damped =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(thrust, drag));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    for (int i = 0; i < 8; i++) {
      new PhysicsEngine().step(world, List.of(driven, damped));
    }

    assertTrue(damped.linearVelocity().x() > 0);
    assertTrue(damped.linearVelocity().x() < driven.linearVelocity().x());
  }

  @Test
  void sameCoefficientsShareTerminalSpeedAcrossMedia() {
    double k = 4;
    double c = 0.02;
    double terminal = Math.sqrt(k / c);
    MediumThrustForce waterThrust =
        new MediumThrustForce(new Vector3d(), new Vector3d(1, 0, 0), k, DensityField.uniform(1000));
    MediumThrustForce airThrust =
        new MediumThrustForce(new Vector3d(), new Vector3d(1, 0, 0), k, DensityField.uniform(1.2));
    QuadraticDragForce waterDrag = new QuadraticDragForce(c, DensityField.uniform(1000));
    QuadraticDragForce airDrag = new QuadraticDragForce(c, DensityField.uniform(1.2));
    BodyImpl waterRest =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(waterThrust, waterDrag));
    BodyImpl airRest =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(airThrust, airDrag));
    BodyImpl waterCruise =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(waterThrust, waterDrag));
    BodyImpl airCruise =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()),
            2,
            List.of(),
            List.of(airThrust, airDrag));
    waterCruise.setLinearVelocity(new Vector3d(terminal, 0, 0));
    airCruise.setLinearVelocity(new Vector3d(terminal, 0, 0));
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());

    new PhysicsEngine().step(world, List.of(waterRest, airRest, waterCruise, airCruise));

    assertTrue(waterRest.linearVelocity().x() > airRest.linearVelocity().x());
    assertEquals(terminal, waterCruise.linearVelocity().x(), 1e-6);
    assertEquals(terminal, airCruise.linearVelocity().x(), 1e-6);
  }
```

If the composition test is written after Task 3, it should pass immediately (the types already exist). That is acceptable: this task is the catalog signature, not a new type. If it fails, fix coefficients so both bodies keep `v_x > 0` and the water body is slower — do not change the force laws.

- [ ] **Step 2: Run the catalog suite**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.CatalogStepTest --tests dev.mintychochip.phys.MediumThrustForceTest --tests dev.mintychochip.phys.QuadraticDragForceTest --tests dev.mintychochip.phys.ThrustForceTest --tests dev.mintychochip.phys.VehicleCompositionTest`

Expected: BUILD SUCCESSFUL. `ThrustForce` and lumped drag tests remain green.

- [ ] **Step 3: Run the quality gate**

Run: `./gradlew check`

Expected: BUILD SUCCESSFUL. Fix any Checkstyle/PMD/SpotBugs on the new files (javadocs on production types, no unused fields, no `ignored` constructor flags).

- [ ] **Step 4: Check off the living spec**

In `docs/specs/physics.md`:

- Mark Next items done:

```markdown
- [x] `MediumThrustForce`: density-scaled actuator at a body-local point with `r × F` torque
- [x] `QuadraticDragForce(c, DensityField)`: `−c · ρ · |v| v`; one-arg constructor stays lumped
```

- Add matching checked items under Current:

```markdown
- [x] `MediumThrustForce` samples `DensityField` at a body-local point and produces `r × F` torque.
- [x] `QuadraticDragForce` optional `DensityField` overload; one-arg lumped law unchanged.
```

- Set `Last updated: 2026-08-17` (already that date if unchanged).
- Do not check “Horizontal movement and water drag for ships” or any ship-gameplay Future item.

- [ ] **Step 5: Commit**

```bash
git add common/src/test/java/dev/mintychochip/phys/CatalogStepTest.java docs/specs/physics.md
git commit -m "test: prove medium thrust and density drag on the catalog step"
```

If the living-spec checkbox edit feels like a separate docs unit after the test commit, split it:

```bash
git add common/src/test/java/dev/mintychochip/phys/CatalogStepTest.java
git commit -m "test: prove medium thrust and density drag on the catalog step"
git add docs/specs/physics.md
git commit -m "docs: record medium thrust and density drag as current"
```

---

## Self-Review

**Spec coverage**

| Spec requirement | Task |
|---|---|
| `MediumThrustForce` constructors, law, validation | Task 1 |
| Default constructor = world liquid, never raw `isFluid` | Task 1 `defaultConstructorUsesWorldLiquidOnly` |
| Offset `τ = r × F`, CoM torque 0, orientation | Task 1 |
| Vacuum `ρ = 0` leaves velocity unchanged | Task 1 + Task 4 |
| `ThrustForce` unchanged | Task 4 re-runs `ThrustForceTest` |
| One-arg `QuadraticDragForce` lumped `−18` | Task 3 keeps existing tests |
| Two-arg `F = −c ρ \|v\| v`, rest = 0 | Task 3 |
| Mean collider density / body position if none | Task 2 `meanDensity` + Task 3 half-submerged test |
| Shared sample grid with buoyancy | Task 2 |
| Composition: drag slows vs thrust alone; same `k`/`c` share `√(k/c)` | Task 4 |
| No ship / command / `World.densityField()` | File map “Do not touch” |

**Placeholder scan:** none. Commands, types, and expected numbers are explicit.

**Type consistency:** `MediumThrustForce(localPoint, localAxis, coefficient[, medium])`, `QuadraticDragForce(coefficient[, medium])`, `DensitySampling.displacedMass` / `meanDensity`. Later tasks use those names only.
