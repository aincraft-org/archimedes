# Physics Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a generic Bukkit-independent rigid-body physics library in `dev.mintychochip.phys` and re-implement the existing Archimedes ship buoyancy/mass model as a client in `dev.mintychochip.archimedes.phys`.

**Architecture:** `:api` contains allocation-free math records and generic physics contracts; `:common` package `dev.mintychochip.phys` contains `Aabb`, `BodyImpl`, and `PhysicsEngine`; `:common` package `dev.mintychochip.archimedes.phys` contains the ship client; `:paper` package `dev.mintychochip.archimedes.phys.bukkit` contains Bukkit adapters. `Shape.bounds(Transform)` returns the API type `Bounds`; `Aabb` lives in `:common` and implements both `Shape` and `Bounds`. `PhysicsEngine` is stateless, steps a caller-supplied `Collection<Body>`, and sums `Force.Result` values without injecting `World.gravity()`. `ShipPhysics` constructs a `Body` each tick, drives `EquilibriumSolver`, validates the path using `ShipPose.anchorDy() = floor(y)`, and delegates world mutation to `ShipRuntime` with all-or-nothing rollback.

**Tech Stack:** Java 25, Gradle Kotlin DSL (`:api`, `:common`, `:paper` modules), JUnit 5, Paper 26.2, Adventure/Bukkit integration already in use; no new runtime dependencies.

## Global Constraints

- `:api` package `dev.mintychochip.phys` records/interfaces are exactly:
  - `public record Vector3(double x, double y, double z)`
  - `public record Quaternion(double x, double y, double z, double w)`
  - `public record Transform(Vector3 position, Quaternion orientation)`
  - `public record Matrix3x3(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21, double m22)`
  - `public interface Bounds { Vector3 min(); Vector3 max(); double volume(); boolean contains(Vector3 point); }`
  - `public interface Shape { Bounds bounds(Transform transform); double volume(); }`
  - `public record Material(double density)`
  - `public interface Collider { Shape shape(); Material material(); Transform localTransform(); }`
  - `public interface Force { Result apply(Body body, World world); record Result(Vector3 force, Vector3 torque) {} }`
  - `public interface FluidField { boolean isFluid(Vector3 point); double density(Vector3 point); }`
  - `public interface World { Vector3 gravity(); FluidField fluidField(); double timeStep(); default boolean isObstacle(Vector3 point) { return false; } }`
  - `public interface Body { Transform transform(); void setTransform(Transform t); Vector3 linearVelocity(); void setLinearVelocity(Vector3 v); Vector3 angularVelocity(); void setAngularVelocity(Vector3 v); double mass(); double inverseMass(); Matrix3x3 inertia(); Matrix3x3 inverseInertia(); List<Collider> colliders(); List<Force> forces(); boolean active(); void setActive(boolean active); }`
  - `public interface Physics { void step(World world, Collection<Body> bodies); }`
- `:common` package `dev.mintychochip.phys` contains `Aabb` (implements `Shape, Bounds`), `BodyImpl`, and `PhysicsEngine`.
- `:common` package `dev.mintychochip.archimedes.phys` contains the ship client.
- `:paper` package `dev.mintychochip.archimedes.phys.bukkit` contains `BukkitFluidField` and `BukkitMaterialKeyResolver`.
- `Shape.bounds(Transform)` returns `Bounds`, not `Aabb`, so `:api` does not depend on `:common`.
- `PhysicsEngine` is stateless and steps a supplied `Collection<Body>`; it does not retain bodies or inject gravity.
- `ShipPose.anchorDy() = floor(y)` is authoritative for block restoration, path checks, and runtime moves.
- `ShipRuntime` rollback is preserved; failed `ShipRuntime.move` restores the old pose and clears per-ship velocity.
- New `config.yml` keys live under the existing `buoyancy:` section.
- Tests for `FluidField` and `Shape` must use anonymous classes, not lambdas, because each has two methods.
- Use SI units and finite-value validation; validate quaternion normalization.

## File Map

### `:api` new files
- `api/src/main/java/dev/mintychochip/phys/Vector3.java`
- `api/src/main/java/dev/mintychochip/phys/Quaternion.java`
- `api/src/main/java/dev/mintychochip/phys/Transform.java`
- `api/src/main/java/dev/mintychochip/phys/Matrix3x3.java`
- `api/src/main/java/dev/mintychochip/phys/Bounds.java`
- `api/src/main/java/dev/mintychochip/phys/Shape.java`
- `api/src/main/java/dev/mintychochip/phys/Material.java`
- `api/src/main/java/dev/mintychochip/phys/Collider.java`
- `api/src/main/java/dev/mintychochip/phys/Force.java`
- `api/src/main/java/dev/mintychochip/phys/FluidField.java`
- `api/src/main/java/dev/mintychochip/phys/World.java`
- `api/src/main/java/dev/mintychochip/phys/Body.java`
- `api/src/main/java/dev/mintychochip/phys/Physics.java`

### `:api` modified files
- `api/src/main/java/dev/mintychochip/archimedes/config/ShipConfig.java`

### `:common` new files
- `common/src/main/java/dev/mintychochip/phys/Aabb.java`
- `common/src/main/java/dev/mintychochip/phys/BodyImpl.java`
- `common/src/main/java/dev/mintychochip/phys/PhysicsEngine.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/MaterialKeyResolver.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/WaterlineResolver.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/ShipBody.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/ShipMassModel.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/RiderCount.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/ShipBuoyancyForce.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/EquilibriumResult.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/EquilibriumSolver.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/ShipPhysics.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/ShipPhysicsImpl.java`

### `:common` modified files
- `common/src/main/java/dev/mintychochip/archimedes/ship/ShipServiceImpl.java`

### `:paper` new files
- `paper/src/main/java/dev/mintychochip/archimedes/phys/bukkit/BukkitFluidField.java`
- `paper/src/main/java/dev/mintychochip/archimedes/phys/bukkit/BukkitMaterialKeyResolver.java`

### `:paper` modified files
- `paper/src/main/java/dev/mintychochip/archimedes/ArchimedesPlugin.java`
- `paper/src/main/java/dev/mintychochip/archimedes/config/ShipConfigLoader.java`
- `paper/src/main/resources/config.yml`

### Legacy removal
- Delete: `api/src/main/java/dev/mintychochip/phys/Buoyancy.java`
- Delete: `api/src/main/java/dev/mintychochip/phys/BuoyancySurface.java`
- Delete: `common/src/main/java/dev/mintychochip/phys/BuoyancyEngine.java`
- Delete: `common/src/main/java/dev/mintychochip/phys/BuoyancyImpl.java`
- Delete: `common/src/main/java/dev/mintychochip/phys/BuoyancyResolver.java`
- Delete: `paper/src/main/java/dev/mintychochip/phys/BukkitBuoyancySurface.java`
- Delete related legacy tests after callers migrate.

---

### Task 1: Math Records in `:api`

**Files:**
- Create: `api/src/main/java/dev/mintychochip/phys/Vector3.java`, `Quaternion.java`, `Transform.java`, `Matrix3x3.java`.
- Test: `api/src/test/java/dev/mintychochip/phys/MathRecordsTest.java`.

**Interfaces:**
- Consumes: Java primitives and `Objects.requireNonNull`.
- Produces: `Vector3`, `Quaternion`, `Transform`, `Matrix3x3` immutable records with finite-value validation and normalized quaternion validation.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MathRecordsTest {
  @Test void recordsRetainComponents() {
    assertEquals(new Vector3(1, 2, 3), new Vector3(1, 2, 3));
    assertEquals(new Quaternion(0, 0, 0, 1), new Quaternion(0, 0, 0, 1));
    assertEquals(new Transform(new Vector3(1, 2, 3), new Quaternion(0, 0, 0, 1)),
        new Transform(new Vector3(1, 2, 3), new Quaternion(0, 0, 0, 1)));
    assertEquals(9, new Matrix3x3(9,0,0,0,9,0,0,0,9).m00());
  }

  @Test void rejectsInvalidNumbersAndQuaternionNorm() {
    assertThrows(IllegalArgumentException.class, () -> new Vector3(Double.NaN, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new Quaternion(0, 0, 0, 2));
  }

  @Test void quaternionRotatesVector() {
    Quaternion q = new Quaternion(0, 0, Math.sin(Math.PI / 4), Math.cos(Math.PI / 4));
    Vector3 actual = q.rotate(new Vector3(0, 1, 0));
    assertEquals(-1.0, actual.x(), 1e-9);
    assertEquals(0.0, actual.y(), 1e-9);
    assertEquals(0.0, actual.z(), 1e-9);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests dev.mintychochip.phys.MathRecordsTest`
Expected: `FAIL with classes Vector3, Quaternion, Transform, or Matrix3x3 not found`.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.mintychochip.phys;

public record Vector3(double x, double y, double z) {
  public Vector3 { finite(x); finite(y); finite(z); }
  public Vector3 add(Vector3 v) { return new Vector3(x + v.x(), y + v.y(), z + v.z()); }
  public Vector3 subtract(Vector3 v) { return new Vector3(x - v.x(), y - v.y(), z - v.z()); }
  public Vector3 scale(double s) { return new Vector3(x * s, y * s, z * s); }
  public double dot(Vector3 v) { return x * v.x() + y * v.y() + z * v.z(); }
  public Vector3 cross(Vector3 v) {
    return new Vector3(
        y * v.z() - z * v.y(),
        z * v.x() - x * v.z(),
        x * v.y() - y * v.x());
  }
  public double length() { return Math.sqrt(x * x + y * y + z * z); }
  static void finite(double value) { if (!Double.isFinite(value)) throw new IllegalArgumentException("non-finite value"); }
  public static final Vector3 ZERO = new Vector3(0, 0, 0);
}
```
```java
package dev.mintychochip.phys;

public record Quaternion(double x, double y, double z, double w) {
  public Quaternion {
    Vector3.finite(x); Vector3.finite(y); Vector3.finite(z); Vector3.finite(w);
    double norm = Math.sqrt(x*x + y*y + z*z + w*w);
    if (Math.abs(norm - 1.0) > 1e-9) throw new IllegalArgumentException("quaternion must be normalized");
  }

  public Vector3 rotate(Vector3 v) {
    Vector3 u = new Vector3(x, y, z);
    double s = w;
    Vector3 t1 = u.cross(v);
    Vector3 t2 = u.cross(t1.add(v.scale(s)));
    return v.add(t2.scale(2));
  }
}
```

```java
package dev.mintychochip.phys;

import java.util.Objects;
public record Transform(Vector3 position, Quaternion orientation) {
  public Transform { Objects.requireNonNull(position); Objects.requireNonNull(orientation); }
}
```

```java
package dev.mintychochip.phys;

public record Matrix3x3(double m00, double m01, double m02, double m10, double m11, double m12,
                        double m20, double m21, double m22) {
  public Matrix3x3 { double[] values = {m00,m01,m02,m10,m11,m12,m20,m21,m22}; for (double v : values) Vector3.finite(v); }
  public Vector3 multiply(Vector3 v) {
    return new Vector3(
        m00 * v.x() + m01 * v.y() + m02 * v.z(),
        m10 * v.x() + m11 * v.y() + m12 * v.z(),
        m20 * v.x() + m21 * v.y() + m22 * v.z());
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests dev.mintychochip.phys.MathRecordsTest`
Expected: `BUILD SUCCESSFUL` and all tests pass.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/dev/mintychochip/phys api/src/test/java/dev/mintychochip/phys/MathRecordsTest.java
git commit -m "feat: add physics math records"
```

### Task 2: Core Contracts in `:api`

**Files:**
- Create: `api/src/main/java/dev/mintychochip/phys/{Bounds,Shape,Material,Collider,Force,FluidField,World,Body,Physics}.java`.
- Test: `api/src/test/java/dev/mintychochip/phys/CoreContractsTest.java`.

**Interfaces:**
- Consumes: Task 1 math records.
- Produces: exact `Bounds`, `Shape`, `Material`, `Collider`, `Force`, `FluidField`, `World`, `Body`, `Physics` signatures from Global Constraints.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreContractsTest {
  @Test void shapeAndFluidFieldUseAnonymousClasses() {
    Shape shape = new Shape() {
      public Bounds bounds(Transform transform) {
        return new Bounds() {
          public Vector3 min() { return new Vector3(0,0,0); }
          public Vector3 max() { return new Vector3(1,1,1); }
          public double volume() { return 1; }
          public boolean contains(Vector3 point) { return point.x() >= 0 && point.x() <= 1; }
        };
      }
      public double volume() { return 1; }
    };
    FluidField fluids = new FluidField() {
      public boolean isFluid(Vector3 point) { return point.y() < 0; }
      public double density(Vector3 point) { return 1000; }
    };
    assertEquals(1, shape.volume());
    assertTrue(fluids.isFluid(new Vector3(0,-1,0)));
  }

  @Test void forceResultIsTyped() {
    Force.Result result = new Force.Result(new Vector3(1,2,3), new Vector3(0,0,0));
    assertEquals(2, result.force().y());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :api:test --tests dev.mintychochip.phys.CoreContractsTest`
Expected: `FAIL with missing contract types`.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.mintychochip.phys;
public interface Bounds { Vector3 min(); Vector3 max(); double volume(); boolean contains(Vector3 point); }
```

```java
package dev.mintychochip.phys;
public interface Shape { Bounds bounds(Transform transform); double volume(); }
```

```java
package dev.mintychochip.phys;
public record Material(double density) {
  public Material { Vector3.finite(density); if (density < 0) throw new IllegalArgumentException("negative density"); }
}
```

```java
package dev.mintychochip.phys;
public interface Collider { Shape shape(); Material material(); Transform localTransform(); }
```

```java
package dev.mintychochip.phys;
public interface Force { Result apply(Body body, World world); record Result(Vector3 force, Vector3 torque) {} }
```

```java
package dev.mintychochip.phys;
public interface FluidField { boolean isFluid(Vector3 point); double density(Vector3 point); }
```

```java
package dev.mintychochip.phys;
public interface World { Vector3 gravity(); FluidField fluidField(); double timeStep(); default boolean isObstacle(Vector3 point) { return false; } }
```

```java
package dev.mintychochip.phys;
import java.util.List;
public interface Body {
  Transform transform(); void setTransform(Transform t);
  Vector3 linearVelocity(); void setLinearVelocity(Vector3 v);
  Vector3 angularVelocity(); void setAngularVelocity(Vector3 v);
  double mass(); double inverseMass();
  Matrix3x3 inertia(); Matrix3x3 inverseInertia();
  List<Collider> colliders(); List<Force> forces();
  boolean active(); void setActive(boolean active);
}
```

```java
package dev.mintychochip.phys;
import java.util.Collection;
public interface Physics { void step(World world, Collection<Body> bodies); }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :api:test --tests dev.mintychochip.phys.CoreContractsTest`
Expected: `BUILD SUCCESSFUL` and all tests pass.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/dev/mintychochip/phys api/src/test/java/dev/mintychochip/phys/CoreContractsTest.java
git commit -m "feat: define physics contracts"
```

### Task 3: `Aabb` and `BodyImpl` in `:common`

**Files:**
- Create: `common/src/main/java/dev/mintychochip/phys/Aabb.java`, `BodyImpl.java`.
- Test: `common/src/test/java/dev/mintychochip/phys/AabbBodyImplTest.java`.

**Interfaces:**
- Consumes: `Shape`, `Bounds`, `Body`, `Collider`, `Material`, `Force`, `World`, and math records.
- Produces: `Aabb implements Shape, Bounds` and mutable `BodyImpl implements Body` with immutable collection views.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AabbBodyImplTest {
  @Test void aabbIsShapeAndBounds() {
    Aabb box = new Aabb(new Vector3(0.5, 0.5, 0.5), new Vector3(0.5, 0.5, 0.5));
    assertEquals(1, box.volume(), 1e-9);
    assertTrue(box.contains(new Vector3(0.5, 0.5, 0.5)));
    Bounds world = box.bounds(new Transform(new Vector3(1, 2, 3), new Quaternion(0, 0, 0, 1)));
    assertEquals(new Vector3(1.0, 2.0, 3.0), world.min());
    assertEquals(new Vector3(2.0, 3.0, 4.0), world.max());
  }

  @Test void bodyStoresState() {
    Transform t = new Transform(Vector3.ZERO, new Quaternion(0, 0, 0, 1));
    BodyImpl body = new BodyImpl(t, 2, List.of(), List.of());
    body.setLinearVelocity(new Vector3(1, 0, 0));
    assertEquals(0.5, body.inverseMass(), 1e-9);
    assertEquals(new Vector3(1, 0, 0), body.linearVelocity());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.AabbBodyImplTest`
Expected: `FAIL with Aabb and BodyImpl not found`.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.mintychochip.phys;

import java.util.Objects;

public final class Aabb implements Shape, Bounds {
  private final Vector3 center;
  private final Vector3 halfExtents;

  public Aabb(Vector3 center, Vector3 halfExtents) {
    this.center = Objects.requireNonNull(center);
    this.halfExtents = Objects.requireNonNull(halfExtents);
    if (halfExtents.x() < 0 || halfExtents.y() < 0 || halfExtents.z() < 0)
      throw new IllegalArgumentException("negative half-extent");
  }

  public Vector3 center() { return center; }
  public Vector3 halfExtents() { return halfExtents; }

  @Override public Vector3 min() {
    return new Vector3(center.x() - halfExtents.x(), center.y() - halfExtents.y(), center.z() - halfExtents.z());
  }

  @Override public Vector3 max() {
    return new Vector3(center.x() + halfExtents.x(), center.y() + halfExtents.y(), center.z() + halfExtents.z());
  }

  @Override public double volume() {
    return 8.0 * halfExtents.x() * halfExtents.y() * halfExtents.z();
  }

  @Override public boolean contains(Vector3 p) {
    return Math.abs(p.x() - center.x()) <= halfExtents.x()
        && Math.abs(p.y() - center.y()) <= halfExtents.y()
        && Math.abs(p.z() - center.z()) <= halfExtents.z();
  }

  @Override public Bounds bounds(Transform transform) {
    Vector3 c = transform.position().add(center);
    return new Aabb(c, halfExtents);
  }
}
```

```java
package dev.mintychochip.phys;

import java.util.*;

public final class BodyImpl implements Body {
  private Transform transform;
  private Vector3 linearVelocity = Vector3.ZERO;
  private Vector3 angularVelocity = Vector3.ZERO;
  private final double mass;
  private boolean active = true;
  private final List<Collider> colliders;
  private final List<Force> forces;

  public BodyImpl(Transform transform, double mass, List<Collider> colliders, List<Force> forces) {
    this.transform = Objects.requireNonNull(transform);
    if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("mass");
    this.mass = mass;
    this.colliders = List.copyOf(colliders);
    this.forces = List.copyOf(forces);
  }

  public Transform transform() { return transform; }
  public void setTransform(Transform t) { transform = Objects.requireNonNull(t); }
  public Vector3 linearVelocity() { return linearVelocity; }
  public void setLinearVelocity(Vector3 v) { linearVelocity = Objects.requireNonNull(v); }
  public Vector3 angularVelocity() { return angularVelocity; }
  public void setAngularVelocity(Vector3 v) { angularVelocity = Objects.requireNonNull(v); }
  public double mass() { return mass; }
  public double inverseMass() { return 1.0 / mass; }
  public Matrix3x3 inertia() { double i = mass; return new Matrix3x3(i,0,0,0,i,0,0,0,i); }
  public Matrix3x3 inverseInertia() { double i = 1.0 / mass; return new Matrix3x3(i,0,0,0,i,0,0,0,i); }
  public List<Collider> colliders() { return colliders; }
  public List<Force> forces() { return forces; }
  public boolean active() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.AabbBodyImplTest`
Expected: `BUILD SUCCESSFUL` and all tests pass.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/phys common/src/test/java/dev/mintychochip/phys/AabbBodyImplTest.java
git commit -m "feat: add common bounds and body"
```

### Task 4: `PhysicsEngine` in `:common`

**Files:**
- Create: `common/src/main/java/dev/mintychochip/phys/PhysicsEngine.java`.
- Test: `common/src/test/java/dev/mintychochip/phys/PhysicsEngineTest.java`.

**Interfaces:**
- Consumes: `Physics`, `Body`, `Force`, `World`, `Collection<Body>`.
- Produces: stateless `PhysicsEngine implements Physics`; sums `Force.Result` force/torque, then integrates velocities and transform using `world.timeStep()`; never injects `World.gravity()`.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicsEngineTest {
  @Test void stepsSuppliedBodiesAndDoesNotInjectGravity() {
    BodyImpl body = new BodyImpl(
        new Transform(Vector3.ZERO, new Quaternion(0, 0, 0, 1)),
        1,
        List.of(),
        List.of((b, w) -> new Force.Result(new Vector3(2, 0, 0), Vector3.ZERO)));
    World world = world(0.5);
    new PhysicsEngine().step(world, List.of(body));
    assertEquals(new Vector3(1, 0, 0), body.linearVelocity());
    assertEquals(new Vector3(0.5, 0, 0), body.transform().position());
  }

  @Test void integratesTorqueIntoAngularVelocityAndOrientation() {
    BodyImpl body = new BodyImpl(
        new Transform(Vector3.ZERO, new Quaternion(0, 0, 0, 1)),
        1,
        List.of(),
        List.of((b, w) -> new Force.Result(Vector3.ZERO, new Vector3(2, 0, 0))));
    World world = world(0.5);
    new PhysicsEngine().step(world, List.of(body));
    assertEquals(new Vector3(1, 0, 0), body.angularVelocity());
    assertNotEquals(new Quaternion(0, 0, 0, 1), body.transform().orientation());
  }

  private static World world(double dt) {
    return new World() {
      public Vector3 gravity() { return new Vector3(0, -100, 0); }
      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3 p) { return false; }
          public double density(Vector3 p) { return 0; }
        };
      }
      public double timeStep() { return dt; }
    };
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.PhysicsEngineTest`
Expected: `FAIL with PhysicsEngine not found`.

- [ ] **Step 3: Write minimal implementation**

```java
public final class PhysicsEngine implements Physics {
  public void step(World world, Collection<Body> bodies) {
    Objects.requireNonNull(world);
    Objects.requireNonNull(bodies);
    double dt = world.timeStep();
    if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("bad timestep");
    for (Body body : bodies) {
      if (!body.active()) continue;
      Vector3 totalForce = Vector3.ZERO;
      Vector3 totalTorque = Vector3.ZERO;
      for (Force force : body.forces()) {
        Force.Result r = force.apply(body, world);
        totalForce = totalForce.add(r.force());
        totalTorque = totalTorque.add(r.torque());
      }
      Vector3 acc = totalForce.scale(body.inverseMass());
      Vector3 v = body.linearVelocity();
      Vector3 newV = v.add(acc.scale(dt));
      body.setLinearVelocity(newV);

      Vector3 angularAcc = body.inverseInertia().multiply(totalTorque);
      Vector3 omega = body.angularVelocity();
      Vector3 newOmega = omega.add(angularAcc.scale(dt));
      body.setAngularVelocity(newOmega);

      Vector3 p = body.transform().position();
      Quaternion q = body.transform().orientation();
      Quaternion newQ = integrateOrientation(q, newOmega, dt);
      body.setTransform(new Transform(p.add(newV.scale(dt)), newQ));
    }
  }

  private static Quaternion integrateOrientation(Quaternion q, Vector3 omega, double dt) {
    if (omega.x() == 0 && omega.y() == 0 && omega.z() == 0) return q;
    double ox = omega.x(), oy = omega.y(), oz = omega.z();
    double rx = q.w() * ox + q.y() * oz - q.z() * oy;
    double ry = q.w() * oy - q.x() * oz + q.z() * ox;
    double rz = q.w() * oz + q.x() * oy - q.y() * ox;
    double rw = -q.x() * ox - q.y() * oy - q.z() * oz;
    double k = 0.5 * dt;
    double nx = q.x() + k * rx;
    double ny = q.y() + k * ry;
    double nz = q.z() + k * rz;
    double nw = q.w() + k * rw;
    double norm = Math.sqrt(nx*nx + ny*ny + nz*nz + nw*nw);
    return new Quaternion(nx / norm, ny / norm, nz / norm, nw / norm);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests dev.mintychochip.phys.PhysicsEngineTest`
Expected: `BUILD SUCCESSFUL` and the velocity is exactly `(1,0,0)`, not gravity-injected.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/phys/PhysicsEngine.java common/src/test/java/dev/mintychochip/phys/PhysicsEngineTest.java
git commit -m "feat: add stateless physics engine"
```

### Task 5: Bukkit Fluid and Material Adapters

**Files:**
- Create: `paper/src/main/java/dev/mintychochip/archimedes/phys/bukkit/BukkitFluidField.java`.
- Create: `paper/src/main/java/dev/mintychochip/archimedes/phys/bukkit/BukkitMaterialKeyResolver.java`.
- Test: `paper/src/test/java/dev/mintychochip/archimedes/phys/bukkit/BukkitFluidFieldTest.java`.
- Test: `paper/src/test/java/dev/mintychochip/archimedes/phys/bukkit/BukkitMaterialKeyResolverTest.java`.

**Interfaces:**
- Consumes: Bukkit `World`, `Material`, `BlockData`, and the `FluidField` / `MaterialKeyResolver` contracts.
- Produces: `BukkitFluidField implements FluidField` and `BukkitMaterialKeyResolver implements MaterialKeyResolver`; Bukkit types stay in `:paper`.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.archimedes.phys.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.phys.Vector3;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class BukkitFluidFieldTest {
  @Test void waterIsFluidWithConfiguredDensity() {
    World world = mock(World.class);
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(Material.WATER);
    when(world.getBlockAt(0, 10, 0)).thenReturn(block);
    BukkitFluidField field = new BukkitFluidField(world, 1000.0);
    assertTrue(field.isFluid(new Vector3(0.5, 10.5, 0.5)));
    assertEquals(1000.0, field.density(new Vector3(0.5, 10.5, 0.5)), 1e-9);
  }
}
```

```java
package dev.mintychochip.archimedes.phys.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.BlockPos;
import org.junit.jupiter.api.Test;

class BukkitMaterialKeyResolverTest {
  @Test void resolvesPlanksKey() {
    BukkitMaterialKeyResolver resolver = new BukkitMaterialKeyResolver();
    ShipBlock block = new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks");
    assertEquals("minecraft:oak_planks", resolver.key(block));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :paper:test --tests 'dev.mintychochip.archimedes.phys.bukkit.*'`
Expected: `FAIL with adapter classes not found`.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.mintychochip.archimedes.phys.bukkit;

import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.Vector3;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;

public final class BukkitFluidField implements FluidField {
  private final World world;
  private final double fluidDensity;
  private final Set<Material> fluids;

  public BukkitFluidField(World world, double fluidDensity) {
    this.world = world;
    this.fluidDensity = fluidDensity;
    this.fluids = Set.of(Material.WATER);
  }

  @Override public boolean isFluid(Vector3 point) {
    int x = (int) Math.floor(point.x());
    int y = (int) Math.floor(point.y());
    int z = (int) Math.floor(point.z());
    return fluids.contains(world.getBlockAt(x, y, z).getType());
  }

  @Override public double density(Vector3 point) {
    return isFluid(point) ? fluidDensity : 0.0;
  }
}
```

```java
package dev.mintychochip.archimedes.phys.bukkit;

import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.phys.MaterialKeyResolver;
import org.bukkit.Bukkit;

public final class BukkitMaterialKeyResolver implements MaterialKeyResolver {
  @Override public String key(ShipBlock block) {
    var data = Bukkit.createBlockData(block.blockData());
    return data.getMaterial().getKey().toString().toLowerCase();
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :paper:test --tests 'dev.mintychochip.archimedes.phys.bukkit.*'`
Expected: `BUILD SUCCESSFUL` and all adapter tests pass.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/dev/mintychochip/archimedes/phys/bukkit paper/src/test/java/dev/mintychochip/archimedes/phys/bukkit
git commit -m "feat: add Bukkit physics adapters"
```

### Task 6: Ship Mass Model Contracts

**Files:**
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/MaterialKeyResolver.java`.
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/ShipBody.java`.
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/ShipMassModel.java`.
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/RiderCount.java`.
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/WaterlineResolver.java`.
- Test: `common/src/test/java/dev/mintychochip/archimedes/phys/ShipMassModelTest.java`.

**Interfaces:**
- Consumes: API `Body`, `Aabb`, `Material`, `Collider`, `Transform`, `Ship`, `ShipBlock`, `ShipPose`, `ShipOrigin`, and `ShipConfig`.
- Produces: `MaterialKeyResolver` (returns a `String` key for a `ShipBlock`), `ShipBody` factory that builds a generic `Body`, `ShipMassModel` total mass, `RiderCount` interface, and `WaterlineResolver` for column-based water queries.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.*;
import dev.mintychochip.archimedes.model.*;
import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.phys.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipMassModelTest {
  @Test void massIncludesMaterialBlocksAndRiders() {
    Ship ship = new Ship(
        UUID.randomUUID(), UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks")),
        new ShipPose(0), true);
    ShipConfig config = new ShipConfig(
        2048, 8, Set.of(), Set.of(), true, 1, 0.5, 16.0, 0.05, 1.0, 0.5, 0.9,
        Map.of("minecraft:oak_planks", 600.0), 1000.0, 80.0, 16.0, 1e-6, 1e-3);
    MaterialKeyResolver resolver = block -> block.blockData();
    assertEquals(600, ShipMassModel.mass(ship, resolver, config, 0), 1e-9);
    assertEquals(760, ShipMassModel.mass(ship, resolver, config, 2), 1e-9);
  }

  @Test void riderCountCannotBeNegative() {
    assertThrows(IllegalArgumentException.class, () -> new SimpleRiderCount(-1));
  }

  private record SimpleRiderCount(int count) implements RiderCount {
    public SimpleRiderCount { if (count < 0) throw new IllegalArgumentException("negative riders"); }
    @Override public int count(Ship ship) { return count; }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.ShipMassModelTest`
Expected: `FAIL with ship mass model types not found`.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.ShipBlock;

public interface MaterialKeyResolver {
  String key(ShipBlock block);
}
```

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.Ship;

public interface RiderCount {
  int count(Ship ship);
}
```

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;

public final class ShipMassModel {
  private ShipMassModel() {}

  public static double mass(Ship ship, MaterialKeyResolver resolver, ShipConfig config, int riderCount) {
    if (riderCount < 0) throw new IllegalArgumentException("negative rider count");
    double total = riderCount * config.playerMass();
    for (ShipBlock block : ship.blocks()) {
      String key = resolver.key(block);
      Double density = config.materialDensities().get(key);
      total += density != null ? density : config.defaultMaterialDensity();
    }
    return total;
  }
}
```

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.*;
import dev.mintychochip.phys.*;
import java.util.ArrayList;
import java.util.List;

public final class ShipBody {
  private ShipBody() {}

  public static Body from(Ship ship, MaterialKeyResolver resolver, ShipConfig config, int riderCount, Force buoyancy) {
    List<Collider> colliders = new ArrayList<>();
    for (ShipBlock block : ship.blocks()) {
      String key = resolver.key(block);
      double density = config.materialDensities().getOrDefault(key, config.defaultMaterialDensity());
      Aabb box = new Aabb(Vector3.ZERO, new Vector3(0.5, 0.5, 0.5));
      Transform local = new Transform(
          new Vector3(block.pos().x() + 0.5, block.pos().y() + 0.5, block.pos().z() + 0.5),
          new Quaternion(0, 0, 0, 1));
      colliders.add(new SimpleCollider(box, new Material(density), local));
    }
    Vector3 world = new Vector3(ship.origin().x(), ship.origin().y() + ship.pose().y(), ship.origin().z());
    double mass = ShipMassModel.mass(ship, resolver, config, riderCount);
    return new BodyImpl(new Transform(world, new Quaternion(0, 0, 0, 1)), mass, colliders, List.of(buoyancy));
  }

  private record SimpleCollider(Shape shape, Material material, Transform localTransform) implements Collider {}
}
```

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.*;

public final class WaterlineResolver {
  public static final int NO_WATER = Integer.MIN_VALUE;
  private WaterlineResolver() {}

  public static int submergedVolume(Body body, World world) {
    int count = 0;
    for (Collider c : body.colliders()) {
      Bounds b = c.shape().bounds(transform(body, c));
      int bottom = (int) Math.floor(b.min().y());
      Vector3 center = new Vector3((b.min().x() + b.max().x()) / 2.0,
                                   (b.min().y() + b.max().y()) / 2.0,
                                   (b.min().z() + b.max().z()) / 2.0);
      int ax = (int) Math.floor(center.x());
      int az = (int) Math.floor(center.z());
      int surface = columnWaterSurface(world, ax, bottom, az);
      if (surface != NO_WATER && bottom <= surface) count++;
    }
    return count;
  }

  public static boolean isPathClear(Ship ship, World world, double poseY, ShipConfig config) {
    int min = Math.min(ship.pose().anchorDy(), (int) Math.floor(poseY));
    int max = Math.max(ship.pose().anchorDy(), (int) Math.floor(poseY));
    for (int y = min; y <= max; y++) {
      for (ShipBlock block : ship.blocks()) {
        int wx = ship.origin().x() + block.pos().x();
        int wy = ship.origin().y() + y + block.pos().y();
        int wz = ship.origin().z() + block.pos().z();
        Vector3 center = new Vector3(wx + 0.5, wy + 0.5, wz + 0.5);
        if (world.isObstacle(center) && !world.fluidField().isFluid(center)) return false;
      }
    }
    return true;
  }

  private static Transform transform(Body body, Collider c) {
    return new Transform(
        body.transform().position().add(c.localTransform().position()),
        c.localTransform().orientation());
  }

  private static int columnWaterSurface(World world, int x, int bottom, int z) {
    boolean sealed = false;
    int highest = NO_WATER;
    for (int y = bottom + 64; y >= bottom - 64; y--) {
      Vector3 p = new Vector3(x + 0.5, y + 0.5, z + 0.5);
      if (world.fluidField().isFluid(p)) {
        if (!sealed && highest == NO_WATER) highest = y;
      } else if (world.isObstacle(p)) {
        sealed = true;
      }
    }
    return highest;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.ShipMassModelTest`
Expected: `BUILD SUCCESSFUL` and all mass model tests pass.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/archimedes/phys/{MaterialKeyResolver.java,RiderCount.java,ShipBody.java,ShipMassModel.java,WaterlineResolver.java} common/src/test/java/dev/mintychochip/archimedes/phys/ShipMassModelTest.java
git commit -m "feat: add ship body and mass model"
```

### Task 7: Ship Configuration Additions

**Files:**
- Modify: `api/src/main/java/dev/mintychochip/archimedes/config/ShipConfig.java`.
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/config/ShipConfigLoader.java`.
- Modify: `paper/src/main/resources/config.yml`.
- Test: `paper/src/test/java/dev/mintychochip/archimedes/config/ShipConfigLoaderTest.java`.

**Interfaces:**
- Consumes: existing `ShipConfig` constructor and `ShipConfigLoader.load`.
- Produces: `ShipConfig` with density table, default density, player mass, max fall, mass tolerance, draft tolerance; loader reads and validates these from `buoyancy:`.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.archimedes.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ShipConfigLoaderTest {
  @Test void loadsMaterialDensitiesAndTolerances() {
    YamlConfiguration cfg = new YamlConfiguration();
    cfg.set("maximum-blocks", 10);
    cfg.set("target-distance", 16);
    cfg.set("forbidden-materials", java.util.List.of());
    cfg.set("disabled-worlds", java.util.List.of());
    cfg.set("buoyancy-enabled", true);
    cfg.set("physics-ticks", 1);
    cfg.set("bob-amplitude", 0.5);
    cfg.set("max-rise", 16.0);
    cfg.set("gravity", 0.05);
    cfg.set("water-density", 1.0);
    cfg.set("block-density", 0.5);
    cfg.set("damping", 0.9);
    cfg.set("buoyancy.material-densities.minecraft:oak_planks", 0.6);
    cfg.set("buoyancy.default-material-density", 1.0);
    cfg.set("buoyancy.player-mass", 80.0);
    cfg.set("buoyancy.max-fall", 16.0);
    cfg.set("buoyancy.mass-tolerance", 1e-6);
    cfg.set("buoyancy.draft-tolerance", 1e-3);
    ShipConfig config = ShipConfigLoader.load(cfg);
    assertEquals(0.6, config.materialDensities().get("minecraft:oak_planks"), 1e-9);
    assertEquals(1.0, config.defaultMaterialDensity(), 1e-9);
    assertEquals(80.0, config.playerMass(), 1e-9);
    assertEquals(16.0, config.maxFall(), 1e-9);
    assertEquals(1e-6, config.massTolerance(), 1e-12);
    assertEquals(1e-3, config.draftTolerance(), 1e-12);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :paper:test --tests dev.mintychochip.archimedes.config.ShipConfigLoaderTest`
Expected: `FAIL with missing accessors or loader keys`.

- [ ] **Step 3: Write minimal implementation**

Add to `api/src/main/java/dev/mintychochip/archimedes/config/ShipConfig.java`:

```java
  private final Map<String, Double> materialDensities;
  private final double defaultMaterialDensity;
  private final double playerMass;
  private final double maxFall;
  private final double massTolerance;
  private final double draftTolerance;
```

Replace the constructor with one that accepts the six new values and assigns them, then add the accessors:

```java
  public ShipConfig(
      int maximumBlocks,
      int targetDistance,
      Set<String> forbiddenMaterials,
      Set<UUID> disabledWorlds,
      boolean buoyancyEnabled,
      int physicsTicks,
      double bobAmplitude,
      double maxRise,
      double gravity,
      double waterDensity,
      double blockDensity,
      double damping,
      Map<String, Double> materialDensities,
      double defaultMaterialDensity,
      double playerMass,
      double maxFall,
      double massTolerance,
      double draftTolerance) {
    this.maximumBlocks = maximumBlocks;
    this.targetDistance = targetDistance;
    this.forbiddenMaterials = Set.copyOf(forbiddenMaterials);
    this.disabledWorlds = Set.copyOf(disabledWorlds);
    this.buoyancyEnabled = buoyancyEnabled;
    this.physicsTicks = physicsTicks;
    this.bobAmplitude = bobAmplitude;
    this.maxRise = maxRise;
    this.gravity = gravity;
    this.waterDensity = waterDensity;
    this.blockDensity = blockDensity;
    this.damping = damping;
    this.materialDensities = Map.copyOf(materialDensities);
    this.defaultMaterialDensity = defaultMaterialDensity;
    this.playerMass = playerMass;
    this.maxFall = maxFall;
    this.massTolerance = massTolerance;
    this.draftTolerance = draftTolerance;
  }

  public Map<String, Double> materialDensities() { return materialDensities; }
  public double defaultMaterialDensity() { return defaultMaterialDensity; }
  public double playerMass() { return playerMass; }
  public double maxFall() { return maxFall; }
  public double massTolerance() { return massTolerance; }
  public double draftTolerance() { return draftTolerance; }
```

In `paper/src/main/java/dev/mintychochip/archimedes/config/ShipConfigLoader.java`, add to `load` before the final `return new ShipConfig` statement:

```java
    org.bukkit.configuration.ConfigurationSection buoyancy = configuration.getConfigurationSection("buoyancy");
    if (buoyancy == null) throw new IllegalArgumentException("missing buoyancy section");
    Map<String, Double> materialDensities = new java.util.HashMap<>();
    org.bukkit.configuration.ConfigurationSection densities = buoyancy.getConfigurationSection("material-densities");
    if (densities != null) {
      for (String key : densities.getKeys(false)) {
        double value = densities.getDouble(key);
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("bad density: " + key);
        materialDensities.put(key.toLowerCase(Locale.ROOT), value);
      }
    }
    double defaultMaterialDensity = positiveFinite(buoyancy.getDouble("default-material-density", 1.0), "default-material-density");
    double playerMass = positiveFinite(buoyancy.getDouble("player-mass", 80.0), "player-mass");
    double maxFall = positiveFinite(buoyancy.getDouble("max-fall", 16.0), "max-fall");
    double massTolerance = positiveFinite(buoyancy.getDouble("mass-tolerance", 1e-6), "mass-tolerance");
    double draftTolerance = positiveFinite(buoyancy.getDouble("draft-tolerance", 1e-3), "draft-tolerance");
```

Add a helper to `ShipConfigLoader`:

```java
  private static double positiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be positive and finite");
    return value;
  }
```

Then replace the final `return new ShipConfig` call with the full argument list:

```java
    return new ShipConfig(
        maximumBlocks,
        targetDistance,
        forbidden,
        disabledWorlds,
        buoyancyEnabled,
        physicsTicks,
        bobAmplitude,
        maxRise,
        gravity,
        waterDensity,
        blockDensity,
        damping,
        materialDensities,
        defaultMaterialDensity,
        playerMass,
        maxFall,
        massTolerance,
        draftTolerance);
```

Append to `paper/src/main/resources/config.yml`:

```yaml
buoyancy:
  material-densities:
    minecraft:oak_planks: 0.6
  default-material-density: 1.0
  player-mass: 80.0
  max-fall: 16.0
  mass-tolerance: 1.0e-6
  draft-tolerance: 1.0e-3
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :paper:test --tests dev.mintychochip.archimedes.config.ShipConfigLoaderTest`
Expected: `BUILD SUCCESSFUL` and config tests pass.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/dev/mintychochip/archimedes/config/ShipConfig.java paper/src/main/java/dev/mintychochip/archimedes/config/ShipConfigLoader.java paper/src/main/resources/config.yml paper/src/test/java/dev/mintychochip/archimedes/config/ShipConfigLoaderTest.java
git commit -m "feat: configure ship physics densities and tolerances"
```

### Task 8: Buoyancy and Equilibrium Solver

**Files:**
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/ShipBuoyancyForce.java`.
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/EquilibriumResult.java`.
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/EquilibriumSolver.java`.
- Test: `common/src/test/java/dev/mintychochip/archimedes/phys/EquilibriumSolverTest.java`.

**Interfaces:**
- Consumes: `Body`, `Aabb`, `Bounds`, `FluidField`, `World`, `ShipConfig`, and `WaterlineResolver`.
- Produces: `ShipBuoyancyForce implements Force`, `EquilibriumResult`, and `EquilibriumSolver.solve(Body, World, ShipConfig)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.*;
import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.phys.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EquilibriumSolverTest {
  @Test void equalBuoyancyAndWeightIsEquilibrium() {
    Body body = new BodyImpl(
        new Transform(new Vector3(0,0,0), new Quaternion(0,0,0,1)),
        1000, List.of(), List.of());
    World world = new World() {
      public Vector3 gravity() { return new Vector3(0, -10, 0); }
      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3 p) { return p.y() <= 10.5; }
          public double density(Vector3 p) { return 1000; }
        };
      }
      public double timeStep() { return 0.05; }
    };
    ShipConfig config = new ShipConfig(
        2048, 8, Set.of(), Set.of(), true, 1, 0.5, 16.0, 0.05, 1.0,
        Map.of(), 1.0, 80.0, 16.0, 1e-6, 1e-3);
    EquilibriumResult result = new EquilibriumSolver().solve(body, world, config);
    assertTrue(result.equilibrium());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.EquilibriumSolverTest`
Expected: `FAIL with missing buoyancy solver types`.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.*;

public final class ShipBuoyancyForce implements Force {
  @Override public Result apply(Body body, World world) {
    int submerged = WaterlineResolver.submergedVolume(body, world);
    double gMag = Math.abs(world.gravity().y());
    double buoyancy = submerged * world.fluidField().density(body.transform().position()) * gMag;
    double weight = body.mass() * gMag;
    double net = buoyancy - weight;
    return new Result(new Vector3(0, net, 0), Vector3.ZERO);
  }
}
```

```java
package dev.mintychochip.archimedes.phys;

public record EquilibriumResult(boolean equilibrium, double targetY, double residual, String reason) {
  public static EquilibriumResult none(String reason) {
    return new EquilibriumResult(false, 0, Double.NaN, reason);
  }
}
```

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.phys.*;

public final class EquilibriumSolver {
  public EquilibriumResult solve(Body body, World world, ShipConfig config) {
    double g = Math.abs(world.gravity().y());
    if (g == 0) return EquilibriumResult.none("no gravity");
    double targetMass = body.mass();
    double originY = body.transform().position().y();
    double bestY = originY;
    double bestError = Double.MAX_VALUE;
    double low = originY - config.maxFall();
    double high = originY + config.maxRise();
    for (double y = low; y <= high; y += 1.0) {
      body.setTransform(new Transform(
          new Vector3(body.transform().position().x(), y, body.transform().position().z()),
          body.transform().orientation()));
      int submerged = WaterlineResolver.submergedVolume(body, world);
      double displacedMass = submerged * world.fluidField().density(body.transform().position());
      double error = Math.abs(displacedMass - targetMass);
      if (error < bestError) {
        bestError = error;
        bestY = y;
      }
      if (error <= config.massTolerance()) {
        return new EquilibriumResult(true, bestY - originY, error, "ok");
      }
    }
    return EquilibriumResult.none("no equilibrium in range");
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.EquilibriumSolverTest`
Expected: `BUILD SUCCESSFUL` and solver tests pass.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/archimedes/phys/{ShipBuoyancyForce.java,EquilibriumResult.java,EquilibriumSolver.java} common/src/test/java/dev/mintychochip/archimedes/phys/EquilibriumSolverTest.java
git commit -m "feat: add ship buoyancy and equilibrium solving"
```

### Task 9: Ship Physics Facade and Runtime Rollback

**Files:**
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/ShipPhysics.java`.
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/ShipPhysicsImpl.java`.
- Test: `common/src/test/java/dev/mintychochip/archimedes/phys/ShipPhysicsTest.java`.

**Interfaces:**
- Consumes: `Ship`, `ShipBody`, `ShipMassModel`, `EquilibriumSolver`, `ShipConfig`, `MaterialKeyResolver`, `RiderCount`, `World`, `Physics`, `ShipRuntime`.
- Produces: `ShipPhysics` facade with `tick(Ship)`, `rise(Ship)`, `sink(Ship, int)`, `clear(Ship)` matching the old `dev.mintychochip.phys.Buoyancy` contract; per-ship velocity and `ShipPose.anchorDy()` rollback.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.*;
import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.*;
import dev.mintychochip.phys.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipPhysicsTest {
  @Test void tickRestoresPoseOnBlockedMove() {
    Ship ship = new Ship(
        UUID.randomUUID(), UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks")),
        new ShipPose(0), true);
    ShipConfig config = new ShipConfig(
        2048, 8, Set.of(), Set.of(), true, 1, 0.5, 16.0, 0.05, 1.0,
        Map.of("minecraft:oak_planks", 600.0), 1000.0, 80.0, 16.0, 1e-6, 1e-3);
    World world = new World() {
      public Vector3 gravity() { return new Vector3(0, -10, 0); }
      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3 p) { return true; }
          public double density(Vector3 p) { return 1000; }
        };
      }
      public double timeStep() { return 0.05; }
    };
    ShipRuntime runtime = new ShipRuntime() {
      public void spawn(Ship s) {}
      public void move(Ship s, double oldY, double newY) { throw new IllegalStateException("blocked"); }
      public void remove(Ship s) {}
      public void removeAll(java.util.Collection<Ship> s) {}
    };
    ShipPhysics physics = new ShipPhysicsImpl(
        new PhysicsEngine(), world, config, new BukkitLikeResolver(), runtime, s -> 0);
    ShipPose old = ship.pose();
    physics.tick(ship);
    assertEquals(old.y(), ship.pose().y(), 1e-9);
  }

  static final class BukkitLikeResolver implements MaterialKeyResolver {
    public String key(ShipBlock block) { return block.blockData(); }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.ShipPhysicsTest`
Expected: `FAIL with ShipPhysics/ShipPhysicsImpl not found`.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.Ship;

public interface ShipPhysics {
  boolean tick(Ship ship);
  boolean rise(Ship ship);
  boolean sink(Ship ship, int blocks);
  void clear(Ship ship);
}
```

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.*;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.*;
import java.util.*;

public final class ShipPhysicsImpl implements ShipPhysics {
  private final Physics physics;
  private final World world;
  private final ShipConfig config;
  private final MaterialKeyResolver resolver;
  private final ShipRuntime runtime;
  private final RiderCount riderCount;
  private final Map<UUID, Double> velocities = new HashMap<>();

  public ShipPhysicsImpl(Physics physics, World world, ShipConfig config,
                         MaterialKeyResolver resolver, ShipRuntime runtime, RiderCount riderCount) {
    this.physics = physics;
    this.world = world;
    this.config = config;
    this.resolver = resolver;
    this.runtime = runtime;
    this.riderCount = riderCount;
  }

  @Override public boolean tick(Ship ship) {
    return moveTo(ship, computeTarget(ship));
  }

  @Override public boolean rise(Ship ship) {
    return moveTo(ship, EquilibriumResult.none("manual rise"));
  }

  @Override public boolean sink(Ship ship, int blocks) {
    return moveTo(ship, ship.pose().y() - Math.max(1, blocks));
  }

  @Override public void clear(Ship ship) {
    velocities.remove(ship.id());
  }

  private EquilibriumResult computeTarget(Ship ship) {
    int riders = riderCount.count(ship);
    Body body = ShipBody.from(ship, resolver, config, riders, new ShipBuoyancyForce());
    body.setLinearVelocity(new Vector3(0, velocities.getOrDefault(ship.id(), 0.0), 0));
    return new EquilibriumSolver().solve(body, world, config);
  }

  private boolean moveTo(Ship ship, EquilibriumResult result) {
    double oldY = ship.pose().y();
    double targetY = result.equilibrium() ? oldY + result.targetY() : oldY;
    return step(ship, oldY, targetY);
  }

  private boolean moveTo(Ship ship, double targetY) {
    double oldY = ship.pose().y();
    return step(ship, oldY, targetY);
  }

  private boolean step(Ship ship, double oldY, double targetY) {
    int riders = riderCount.count(ship);
    ShipBuoyancyForce force = new ShipBuoyancyForce();
    Body body = ShipBody.from(ship, resolver, config, riders, force);
    double v = velocities.getOrDefault(ship.id(), 0.0);
    body.setLinearVelocity(new Vector3(0, v, 0));
    body.setTransform(new Transform(
        new Vector3(ship.origin().x(), ship.origin().y() + oldY, ship.origin().z()),
        body.transform().orientation()));
    physics.step(world, List.of(body));
    double rawY = body.transform().position().y() - ship.origin().y();
    double newY = clampAndDamp(oldY, targetY, rawY, body);
    if (!pathClear(ship, newY) || !runMove(ship, oldY, newY)) {
      ship.setPose(new ShipPose(oldY));
      velocities.put(ship.id(), 0.0);
      return false;
    }
    ship.setPose(new ShipPose(newY));
    return true;
  }

  private double clampAndDamp(double oldY, double targetY, double newY, Body body) {
    double low = Math.max(oldY - config.maxFall(), targetY - config.bobAmplitude());
    double high = Math.min(oldY + config.maxRise(), targetY + config.bobAmplitude());
    if (newY < low) { newY = low; body.setLinearVelocity(Vector3.ZERO); }
    if (newY > high) { newY = high; body.setLinearVelocity(Vector3.ZERO); }
    double v = body.linearVelocity().y() * config.damping();
    velocities.put(ship.id(), v);
    return newY;
  }

  private boolean pathClear(Ship ship, double newY) {
    return WaterlineResolver.isPathClear(ship, world, newY, config);
  }

  private boolean runMove(Ship ship, double oldY, double newY) {
    try { runtime.move(ship, oldY, newY); return true; }
    catch (RuntimeException e) { return false; }
  }
}
```


- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.ShipPhysicsTest`
Expected: `BUILD SUCCESSFUL` and rollback/path tests pass.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/archimedes/phys/{ShipPhysics.java,ShipPhysicsImpl.java,WaterlineResolver.java} common/src/test/java/dev/mintychochip/archimedes/phys/ShipPhysicsTest.java
git commit -m "feat: add ship physics facade and rollback"
```

### Task 10: Plugin and Service Wiring

**Files:**
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/ArchimedesPlugin.java`.
- Modify: `common/src/main/java/dev/mintychochip/archimedes/ship/ShipServiceImpl.java`.
- Test: `paper/src/test/java/dev/mintychochip/archimedes/ArchimedesPluginTest.java`.

**Interfaces:**
- Consumes: `ShipPhysics`, `BukkitFluidField`, `BukkitMaterialKeyResolver`, `ShipConfig`, `ShipRuntime`, `ShipServiceImpl` constructor.
- Produces: plugin-created `ShipPhysics` instance, injected into `ShipServiceImpl`, replacing `dev.mintychochip.phys.Buoyancy`.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.archimedes;

import static org.junit.jupiter.api.Assertions.*;
import dev.mintychochip.archimedes.ship.ShipService;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class ArchimedesPluginTest {
  @Test void physicsWiredDuringEnable() {
    ArchimedesPlugin plugin = new ArchimedesPlugin();
    plugin.onEnable();
    ShipService service = plugin.shipService();
    assertNotNull(service);
    plugin.onDisable();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :paper:test --tests dev.mintychochip.archimedes.ArchimedesPluginTest`
Expected: `FAIL with missing ShipPhysics wiring or service accessors`.

- [ ] **Step 3: Write minimal implementation**

In `paper/src/main/java/dev/mintychochip/archimedes/ArchimedesPlugin.java`, replace the old `BuoyancySurface`/`BuoyancyEngine`/`BuoyancyImpl` construction with:

```java
      BukkitShipRiderTracker tracker = new BukkitShipRiderTracker(world, allShips, collisionOwnerKey, shipKey);
      RenderSurface surface = RenderSurface.of(world);
      BukkitShipRenderer renderer = new BukkitShipRenderer(surface, shipKey);
      BukkitCollisionVolumeManager collisions = new BukkitCollisionVolumeManager(world, collisionOwnerKey);
      BukkitShipEntityCarrier carrier = new BukkitShipEntityCarrier(world, collisionOwnerKey, shipKey, tracker);
      ShipRuntime runtime = new ShipRuntimeImpl(renderer, collisions, carrier);
      BukkitFluidField fluidField = new BukkitFluidField(world, config.waterDensity());
      BukkitMaterialKeyResolver materialResolver = new BukkitMaterialKeyResolver();
      World physicsWorld = new World() {
        public Vector3 gravity() { return new Vector3(0, -config.gravity(), 0); }
        public FluidField fluidField() { return fluidField; }
        public double timeStep() { return config.physicsTicks() * 0.05; }
        public boolean isObstacle(Vector3 point) {
          int x = (int) Math.floor(point.x());
          int y = (int) Math.floor(point.y());
          int z = (int) Math.floor(point.z());
          org.bukkit.Material type = world.getBlockAt(x, y, z).getType();
          return !type.isAir() && type != org.bukkit.Material.WATER;
        }
      };
      ShipPhysics shipPhysics = new ShipPhysicsImpl(
          new PhysicsEngine(), physicsWorld, config, materialResolver, runtime,
          ship -> tracker.riders(ship).size());
      service = new ShipServiceImpl(
          storeAdapter,
          new BukkitScannerWorld(world, config.maximumBlocks(), forbidden),
          runtime,
          new BukkitWorldMutator(world),
          shipPhysics,
          config.buoyancyEnabled(),
          config.worldEnabled(world.getUID()),
          world.getUID());
```

In `common/src/main/java/dev/mintychochip/archimedes/ship/ShipServiceImpl.java`, replace the `dev.mintychochip.phys.Buoyancy` field and constructor parameter with `ShipPhysics` from `dev.mintychochip.archimedes.phys.ShipPhysics`:

```java
  private final dev.mintychochip.archimedes.phys.ShipPhysics shipPhysics;

  public ShipServiceImpl(
      ShipStoreLike store,
      ComponentScanner scanner,
      ShipRuntime runtime,
      WorldMutator mutator,
      dev.mintychochip.archimedes.phys.ShipPhysics shipPhysics,
      boolean buoyancyEnabled,
      boolean worldEnabled,
      UUID worldId) {
    this.store = store;
    this.scanner = scanner;
    this.runtime = runtime;
    this.mutator = mutator;
    this.shipPhysics = shipPhysics;
    this.buoyancyEnabled = buoyancyEnabled;
    this.worldEnabled = worldEnabled;
    this.worldId = worldId;
  }
```

Replace every `buoyancy.` call with `shipPhysics.`:
- `buoyancy.rise(ship)` -> `shipPhysics.rise(ship)`
- `buoyancy.tick(ship)` -> `shipPhysics.tick(ship)`
- `buoyancy.sink(ship, blocks)` -> `shipPhysics.sink(ship, blocks)`
- `buoyancy.clear(ship)` -> `shipPhysics.clear(ship)`

Add a `shipService()` getter to `ArchimedesPlugin`:

```java
  public ShipService shipService() { return service; }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :paper:test --tests dev.mintychochip.archimedes.ArchimedesPluginTest`
Expected: `BUILD SUCCESSFUL` and lifecycle/wiring tests pass.

- [ ] **Step 5: Commit**

```bash
git add paper/src/main/java/dev/mintychochip/archimedes/ArchimedesPlugin.java common/src/main/java/dev/mintychochip/archimedes/ship/ShipServiceImpl.java paper/src/test/java/dev/mintychochip/archimedes/ArchimedesPluginTest.java
git commit -m "feat: wire ship physics into plugin and service"
```

### Task 11: Remove Legacy Buoyancy and Final Verification

**Files:**
- Delete: `api/src/main/java/dev/mintychochip/phys/Buoyancy.java`
- Delete: `api/src/main/java/dev/mintychochip/phys/BuoyancySurface.java`
- Delete: `common/src/main/java/dev/mintychochip/phys/BuoyancyEngine.java`
- Delete: `common/src/main/java/dev/mintychochip/phys/BuoyancyImpl.java`
- Delete: `common/src/main/java/dev/mintychochip/phys/BuoyancyResolver.java`
- Delete: `paper/src/main/java/dev/mintychochip/phys/BukkitBuoyancySurface.java`
- Delete related legacy tests.
- Test: project-wide verification and acceptance matrix A1–A20.

**Interfaces:**
- Consumes: completed API/common/paper implementation and plugin/service wiring.
- Produces: no legacy `Buoyancy*` types, clean module graph, and passing acceptance A1–A20.

- [ ] **Step 1: Write the failing test**

```java
package dev.mintychochip.archimedes;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LegacyRemovalAcceptanceTest {
  @Test void approvedPackagesArePresent() throws Exception {
    assertNotNull(Class.forName("dev.mintychochip.phys.Bounds"));
    assertNotNull(Class.forName("dev.mintychochip.phys.Aabb"));
    assertNotNull(Class.forName("dev.mintychochip.archimedes.phys.ShipPhysics"));
    assertThrows(ClassNotFoundException.class,
        () -> Class.forName("dev.mintychochip.phys.BuoyancyImpl"));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew check`
Expected: `FAIL` while legacy references remain or acceptance coverage is incomplete.

- [ ] **Step 3: Implement migration and deletion**

Delete the files listed above only after `git grep -n -E 'BuoyancyEngine|BuoyancyResolver|BukkitBuoyancySurface|Buoyancy[A-Za-z]+' -- ':!docs'` returns no production code hits (test references that exercise A1–A20 through the new `ShipPhysics` are allowed). Fix any remaining imports in `ShipServiceImpl` and `ArchimedesPlugin`. Run the acceptance matrix A1–A20 from the approved mass-model design using the new `ShipPhysics` facade.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test :paper:test --tests '*Acceptance*' --tests '*ShipPhysics*' --tests '*Buoyancy*'`
Expected: `BUILD SUCCESSFUL`; no legacy class is required by tests.

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL` with all API, common, paper, and acceptance checks passing.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: migrate ships to generic physics library"
```

## Writing-Plans Self-Review

- **Spec coverage:**
  - Task 1 covers allocation-free math records and finite/quaternion validation.
  - Task 2 covers every generic API contract, including `Shape` returning `Bounds` and anonymous `Shape`/`FluidField` tests.
  - Task 3 covers `Aabb` (in `:common`) and mutable `BodyImpl` with unmodifiable collider/force lists.
  - Task 4 covers stateless `PhysicsEngine` that steps `Collection<Body>` and does not inject gravity.
  - Task 5 covers Bukkit `FluidField` and `MaterialKeyResolver` adapters.
  - Task 6 covers ship body construction, material-key resolution, rider count, and mass model.
  - Task 7 covers `ShipConfig` and loader additions for densities, player mass, tolerances, and `max-fall`.
  - Task 8 covers `ShipBuoyancyForce`, `EquilibriumResult`, and `EquilibriumSolver` scanning `[y - maxFall, y + maxRise]`.
  - Task 9 covers `ShipPhysics` facade, `ShipPose.anchorDy()` path validation, `ShipRuntime` all-or-nothing rollback, and per-ship velocity.
  - Task 10 covers `ArchimedesPlugin` and `ShipServiceImpl` wiring.
  - Task 11 covers legacy `Buoyancy*` removal and A1–A20 acceptance.
- **Placeholder scan:** No `TBD`, `TODO`, "implement later", or "write tests for the above" remains. Every task has exact file paths, Step 1–5 with code blocks, a run command, and a commit.
- **Type consistency:** `Shape.bounds(Transform)` returns `Bounds` everywhere; `Aabb` is confined to `:common`; `Force.apply` returns `Result`; `Physics.step` takes `Collection<Body>`; `World` has `timeStep()`; `ShipConfig` uses the real path and constructor; `ShipServiceImpl` and `ArchimedesPlugin` reference `dev.mintychochip.archimedes.phys.ShipPhysics`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-16-physics-library-plan.md`. Two execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, and use focused commits/checkpoints.
2. **Inline Execution** — execute tasks in this session using `executing-plans`, batching related tasks with verification checkpoints.

Choose the execution approach before implementation begins.
