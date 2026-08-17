# Physics Library — Generic Rigid-Body Framework with Ship Client

> **Status:** Direction approved; detailed spec pending review.
> **Related:** `docs/specs/physics.md` (living spec), `docs/superpowers/specs/2026-08-16-buoyancy-mass-model-design.md` (ship mass model).

## Goal

Refactor `dev.mintychochip.phys` from a ship-specific vertical-buoyancy package into a generic, Bukkit-independent rigid-body physics library. The existing ship mass/buoyancy model is preserved and becomes a client (`dev.mintychochip.archimedes.phys`) that uses the generic core.

## Scope

### In scope for this milestone

- Generic physics core in `dev.mintychochip.phys` (`:api` and `:common`).
- Minimal math types (`Vector3`, `Quaternion`, `Transform`).
- `Body`, `Collider`, `Shape`, `Material`, `Force`, `World`, `FluidField`, and `Physics` abstractions.
- `PhysicsEngine` default implementation (semi-implicit Euler integration).
- Ship client in `dev.mintychochip.archimedes.phys` that implements the already-approved mass model:
  - per-material density table,
  - tracked-player rider load,
  - force-balance equilibrium solver,
  - explicit no-equilibrium diagnostics.
- `:paper` Bukkit adapters (`BukkitFluidField`, `BukkitMaterialKeyResolver`, `ShipConfigLoader` additions).
- Migration of existing `Buoyancy*`, `BuoyancyEngine`, `BuoyancyResolver`, and `BukkitBuoyancySurface` out of the generic package.

### Out of scope / non-goals

- Horizontal or rotational ship motion.
- Propulsion, steering, yaw, fuel.
- Ship-vs-ship or dynamic terrain collision modeling.
- Multi-ship water displacement or a shared fluid simulation.
- Water entry effects, drowning, or damage.
- General constraint solving, joints, friction, or restitution (friction/restitution fields may exist but are not implemented).

## Domain vocabulary

- **Body** — a single rigid body with transform, linear/angular velocity, mass, inertia, and an attached collection of `Collider`s and `Force`s.
- **Collider** — a shape + material + local transform attached to a body.
- **Shape** — a geometric primitive; first concrete type is `Aabb`.
- **Material** — physical properties of a collider; first use is `density`.
- **Force** — a function of body, world, and timestep that returns a force and optional torque.
- **FluidField** — world query for whether a point is in fluid and the fluid density there.
- **World** — gravity vector, fluid field, and timestep for a simulation.
- **Physics** / **PhysicsEngine** — stateless engine that steps a caller-supplied collection of bodies.

## Module and package split

- `:api` / `dev.mintychochip.phys` — generic public interfaces and math records.
- `:common` / `dev.mintychochip.phys` — generic default implementations (`BodyImpl`, `Aabb`, `PhysicsEngineImpl`).
- `:common` / `dev.mintychochip.archimedes.phys` — ship client (testable without Bukkit except for block-data string parsing, which is abstracted).
- `:paper` / `dev.mintychochip.archimedes.phys` / `bukkit` — Bukkit `World`/`BlockData` adapters and `ShipConfigLoader` additions.
- `:paper` / `dev.mintychochip.archimedes.ArchimedesPlugin` — wiring.

## Core API

### Math records

```java
public record Vector3(double x, double y, double z) { … }
public record Quaternion(double x, double y, double z, double w) { … }
public record Transform(Vector3 position, Quaternion orientation) { … }
public record Matrix3x3(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21, double m22) { … }
```

These are intentionally minimal; only `Vector3` is fully exercised in this milestone.

### Body

```java
public interface Body {
  Transform transform();
  void setTransform(Transform transform);

  Vector3 linearVelocity();
  void setLinearVelocity(Vector3 v);

  Vector3 angularVelocity();
  void setAngularVelocity(Vector3 v);

  double mass();
  double inverseMass();

  // Inertia is a 3x3 matrix; for the first milestone it may be a diagonal approximation.
  Matrix3x3 inertia();
  Matrix3x3 inverseInertia();

  /** Unmodifiable list of colliders supplied at body construction. */
  List<Collider> colliders();

  /** Unmodifiable list of forces supplied at body construction. */
  List<Force> forces();

  boolean active();
  void setActive(boolean active);
}
```

`PhysicsEngine` may use a default `BodyImpl` record or class.

### Collider, Shape, Material

```java
public interface Collider {
  Shape shape();
  Material material();
  Transform localTransform();
}

public interface Shape {
  Bounds bounds(Transform worldTransform);
  double volume();
}

// :common only:
public final class Aabb implements Shape, Bounds { … }

public record Material(double density) { … }
```

`Aabb` is the only `Shape` in this milestone.

### Force

```java
public interface Force {
  /** Computes a force and torque for the body in the given world this tick. */
  Result apply(Body body, World world);

  record Result(Vector3 force, Vector3 torque) {}
}
```

For the ship client `ShipBuoyancyForce`, `torque` is the zero vector.

### World and FluidField

```java
public interface FluidField {
  boolean isFluid(Vector3 point);
  double density(Vector3 point);
}

public interface World {
  Vector3 gravity();
  FluidField fluidField();
  double timeStep();
  /** True when the point is inside solid, non-fluid matter. */
  default boolean isObstacle(Vector3 point) { return false; }
}
```

### Physics

```java
public interface Physics {
  /**
   * Steps the supplied bodies for one fixed timestep using the given world.
   * The engine does not retain the bodies between calls.
   */
  void step(World world, Collection<Body> bodies);
}
```


`PhysicsEngine` in `:common` is the default implementation. Each tick it accumulates the `force` and `torque` returned by every `Force` on each body, then integrates linear and angular velocity and position using semi-implicit Euler.

`Body` instances are built with their `Collider`s and `Force`s. Both lists are unmodifiable after construction; adding or removing a collider or force requires constructing a new body. This keeps a body deterministic and thread-safe for the duration of a step. The engine does not cache force results; it calls `Force.apply` every tick for every force on the body and sums the returned vectors.

## Ship client

The ship client lives in `dev.mintychochip.archimedes.phys` and is the only place that knows about `Ship`, `ShipBlock`, `ShipRuntime`, and Bukkit material strings.

### Key types

- `ShipBody` — a wrapper/factory that converts a `Ship` into a generic `Body`:
  - one `Aabb` collider per captured block,
  - each collider has a `Material` whose density comes from the configured density table,
  - the body transform starts at the ship's current pose.
- `MaterialKeyResolver` — interface that maps `ShipBlock.blockData()` to a canonical namespaced material key.
- `ShipMassModel` — computes aggregate block mass from collider materials using a `MaterialKeyResolver` and the density table, and adds tracked-player rider mass.
- `ShipBuoyancyForce` — implements `Force`. For each `Aabb` collider it queries the `FluidField` and sums displaced volume; returns net vertical force (buoyancy − weight).
- `EquilibriumSolver` — bounded, deterministic, monotonicity-guarded force-balance solve over `[y − maxFall, y + maxRise]`, producing an `EquilibriumResult` with explicit no-equilibrium states.
- `ShipPhysics` — facade used by `ShipService`. On `tick` it:
  1. snapshots tracked riders and computes `riderMass`,
  2. builds a `ShipBody`, resolves material keys, and computes `totalMass`,
  3. computes the current displaced volume via `ShipBuoyancyForce`,
  4. runs `EquilibriumSolver` to find a target pose,
  5. asks `PhysicsEngine` to integrate one step,
  6. validates the path and calls `ShipRuntime.move` all-or-nothing.

### Path validation and runtime

The ship client reuses `ShipRuntime` for the actual world mutation. If a step is blocked or the runtime fails, the ship's pose is restored and per-ship state is cleared, preserving the existing transaction contract.

## `:paper` adapters

- `BukkitFluidField` — implements `FluidField` using Bukkit `World.getBlockAt(...).getType()`; water density is the configured `water-density`.
- `BukkitMaterialKeyResolver` — implements `dev.mintychochip.archimedes.phys.MaterialKeyResolver`; parses the `ShipBlock.blockData()` string into a Bukkit `BlockData` and returns `NamespacedKey.toString()` lowercased.
- `ShipConfigLoader` additions:
  - `buoyancy.material-densities` map of `namespace:path -> double`,
  - `buoyancy.default-material-density`,
  - `buoyancy.player-mass`,
  - `buoyancy.max-fall`,
  - `buoyancy.mass-tolerance`,
  - `buoyancy.draft-tolerance`.
- `ArchimedesPlugin` constructs `BukkitMaterialKeyResolver`, `BukkitFluidField`, `ShipPhysics`, and passes them to `ShipServiceImpl`.

## Data flow per ship tick

1. `ShipService.tick()` calls `ShipPhysics.tick(ship)`.
2. `ShipPhysics` snapshots tracked riders and computes `riderMass`.
3. `ShipBody` creates a generic `Body` with one `Aabb` collider per block, each `Material` resolved from the configured density table using the material key resolver.
4. `ShipMassModel` computes `totalMass = blockMass + riderMass`.
5. `ShipBuoyancyForce` samples each collider against the `FluidField` and returns net vertical force.
6. `EquilibriumSolver` scans the bounded interval for a target where `displacedVolume * waterDensity == totalMass`, returning explicit no-equilibrium states.
7. `PhysicsEngine` integrates `a = F / totalMass`; `v' = (v + a·dt) · damping`; `y' = y + v'·dt`; clamps to bounds.
8. If the pose changed, the ship client validates the integer-cell path is clear, then calls `ShipRuntime.move(oldY, newY)`.
9. On blocked path or runtime failure, the old pose is restored and velocity is cleared.

## Invariants

- `dev.mintychochip.phys` is Bukkit-free and ship-free.
- `dev.mintychochip.archimedes.phys` is the only consumer of `Ship` and `ShipRuntime` in the physics layer.
- A `Body`'s mass is the sum of its collider volumes × material densities plus any client-added load.
- `ShipPose.anchorDy() = floor(y)` remains authoritative for block restoration, collision volumes, and persistence.
- No `ships.json` schema changes.
- No-equilibrium is a diagnostic, not an exception.
- All-or-nothing moves and transaction rollback are preserved.

## Configuration

New `config.yml` keys extend the existing `buoyancy:` section:
```yaml
buoyancy:
  material-densities:
    minecraft:oak_planks: 0.60
    minecraft:stone: 2.70
  default-material-density: 1.00
  player-mass: 1.00
  max-fall: 16.0
  mass-tolerance: 1.0e-6
  draft-tolerance: 1.0e-3
```

Validation:
- normalized keys, duplicate normalized keys fail enable,
- non-numeric, zero, negative, NaN, or infinite densities fail enable,
- missing optional values use documented defaults,
- unknown runtime materials use the configured default.

## Testing

- `:common` unit tests for `Vector3`, `Aabb`, `BodyImpl`, `PhysicsEngineImpl`.
- `:common` unit tests for `ShipMassModel`, `ShipBuoyancyForce`, `EquilibriumSolver`, `ShipPhysics`.
- `:paper` tests for `ShipConfigLoader` validation and `BukkitMaterialKeyResolver`.
- Acceptance matrix A1–A20 from the approved mass-model design.
- Quality gate: `./gradlew check` (Spotless, Checkstyle, PMD, SpotBugs, Java 25).

## Horizon

- **Current:** generic core + ship client mass model.
- **Next:** horizontal movement, drag, and a `PhysicsWorld` that steps multiple bodies.
- **Future:** propulsion, rotation, ship-vs-ship interaction, multi-ship fluid displacement.

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | Ships are a client of a generic `phys` library, not the owner of the package | User directive: the library is not just for ships |
| 2026-08-16 | Minimal interface core (Body/Collider/Force/World) rather than a managed world or component system | User choice: simple, testable, easy to evolve |
| 2026-08-16 | Geometry represented by `List<Collider>` with primitive `Shape`s; first shape is `Aabb` | User choice; maps naturally to ship block clouds and stays generic |
| 2026-08-16 | Material parsing (`BlockData` → key) stays in `:paper`; generic core has no Bukkit references | Keeps core unit-testable and reusable |
| 2026-08-16 | `Physics` is stateless and steps a caller-supplied collection; body lists are unmodifiable | Deterministic, testable, no hidden ownership |
| 2026-08-16 | `Force.apply` uses `World.timeStep()`; `dt` is not an extra parameter | Avoids redundant timestep arguments |
| 2026-08-16 | `EquilibriumSolver` returns a target pose `y`; integration chases it with damping/clamps | Matches existing damped bobbing contract |
| 2026-08-16 | New density/tolerance keys live under the existing `buoyancy:` section | Keeps related buoyancy config together |
## Open questions

None currently; resolved before implementation planning.
