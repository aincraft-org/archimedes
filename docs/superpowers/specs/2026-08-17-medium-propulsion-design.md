# Medium-Coupled Thrust and Drag

> **Status: Approved on 2026-08-17.** This document is the accepted design contract. Implementation remains a separate plan.
> **Related:** `docs/specs/physics.md` (living spec)

## Goal

Add two generic catalog units so a watercraft now and an airship later share the same forces: a density-scaled actuator with an application point, and an opt-in density-scaled quadratic drag. Vehicle type stays a force list. Archimedes ship gameplay is not wired.

## Scope

### In scope

- `MediumThrustForce` in `dev.mintychochip.phys` (`:common`).
- Optional `DensityField` overload on existing `QuadraticDragForce`.
- Shared collider density sampling so drag and `FluidBuoyancyForce` use one grid.
- Unit tests plus real `Physics.step` signatures for every new law.

### Out of scope

- Horizontal or rotational Archimedes ship gameplay (`ShipPose` stays a scalar `y`).
- Engine blocks, fuel, player throttle, `/ship sail`, or `ShipRuntime` 6DOF.
- Changing `ThrustForce` (density-blind rocket).
- Changing `LiftForce`, `ViscousDragForce`, or `AngularDragForce`.
- Adding `World.densityField()`.
- Wind fields, intake/exhaust plumbing, stall, control surfaces.

## Architecture

Callers attach forces; they do not subclass `Body`.

- Water screw: `MediumThrustForce(point, axis, k)` plus `QuadraticDragForce(c, DensityField.liquid(fluids))`.
- Air prop: the same types with `DensityField.uniform(ρ_air)` (or a later atmosphere field).
- Rocket: existing `ThrustForce`.

`MediumThrust` with no medium uses `DensityField.liquid(world.fluidField())` at apply time. It never reads `FluidField.isFluid` except through that liquid adapter. Atmosphere is always a `DensityField`.

Forces stay immutable. Throttle is the coefficient baked in when the caller constructs the body.

When the same `k` and `c` both scale with the same `ρ`, terminal speed is `√(k/c)` and does not depend on the medium. Density changes how hard the vehicle pushes and how hard the fluid resists, not the cruise speed of that coefficient pair. Different vehicles still differ by choosing different `k` and `c`.

## MediumThrustForce

```text
MediumThrustForce(Vector3dc localPoint, Vector3dc localAxis, double coefficient)
MediumThrustForce(Vector3dc localPoint, Vector3dc localAxis, double coefficient, DensityField medium)
```

### Storage

- `localPoint` — finite body-frame application point. Zero is allowed (thrust through the origin, zero torque).
- `localAxis` — non-zero finite body-frame axis, normalized on store.
- `coefficient` — finite, `≥ 0`.
- `medium` — `null` means world’s liquid at apply time; non-null is used as given.

### Law

```text
p   = X + R(localPoint)
n̂  = R(localAxis)
ρ   = medium.density(p)                    // or DensityField.liquid(world.fluidField())
F   = coefficient · ρ · n̂
τ   = (p − X) × F
```

`X` and `R` are the body’s world position and orientation. `ρ = 0` produces zero force and zero torque (vacuum, dry liquid default, or a field that is zero at `p`).

No extra couple, no `isFluid` gate, no sampling of collider volume.

### Validation

- Null `localPoint`, `localAxis`, `body`, or `world` → `NullPointerException`. The explicit-medium constructor also rejects a null `medium`.
- Non-finite point or axis components → `IllegalArgumentException`.
- Zero-length axis → `IllegalArgumentException`.
- Non-finite or negative coefficient → `IllegalArgumentException`.

## QuadraticDragForce

Keep the existing one-arg constructor unchanged:

```text
QuadraticDragForce(double coefficient)          // F = −c |v| v     (lumped; ρ is inside c)
QuadraticDragForce(double coefficient, DensityField medium)  // F = −c · ρ · |v| v
```

`medium` on the two-arg constructor is required and non-null. Null must not mean “world liquid” — that would change today’s one-arg meaning. World liquid is `new QuadraticDragForce(c, DensityField.liquid(fluids))`.

### Density sample

- If the body has one or more colliders: `ρ` is the volume-weighted mean density using the same sample grid as `FluidBuoyancyForce` (`displacedMass / totalVolume`). Zero-volume colliders contribute nothing.
- If the body has no colliders, or total volume is zero: `ρ = medium.density(body.transform().position())`.
- Torque is always zero.

Rest (`|v| = 0`) is zero force in both constructors.

### Validation

Unchanged for the one-arg constructor. Two-arg: null medium → `NullPointerException`; coefficient rules stay finite and `≥ 0`.

## Shared sampling

Extract the existing `FluidBuoyancyForce` grid into a package-private `DensitySampling` helper used by buoyancy and density drag. Behavior of current buoyancy tests must not change. Known existing limit: collider world centers are `body.position + local.position` and do not rotate the collider offset. Drag inherits that. Medium thrust *does* rotate its application point.

## Testing

Every new law has an `apply` assertion and a `Physics.step` assertion.

| Case | Expected |
|---|---|
| Medium thrust, vacuum / `ρ = 0` | `F = 0`, `τ = 0`; velocity unchanged after `step` |
| Same `k`, `ρ = 1000` vs `ρ = 1.2` | force ratio `1000 / 1.2` |
| CoM application point | `τ = 0` |
| Offset point `(0,0,1)`, axis `+X`, `k = 1`, `ρ = 1000` | `F = (1000,0,0)`, `τ = (0,1000,0)`; `step` produces `ω_y > 0` |
| Body yawed `+π/2` about Y | world axis matches existing `ThrustForce` rotation `(1,0,0) → (0,0,−1)` |
| Default (liquid) constructor in vacuum | no thrust |
| Lumped `QuadraticDragForce(2)` at `v = (3,0,0)` | `F = (−18,0,0)` — existing tests stay green |
| Density drag, same `c`, `ρ = 1000` vs `ρ = 1.2` | faster speed bleed in the denser medium after `step` |
| Density drag at rest | `F = 0` |
| Medium thrust + density drag vs thrust alone, same medium | drag pair is slower after several steps |
| Medium thrust + density drag, same `k`/`c`, two media, from rest | after one step the denser medium is faster (drag is 0 at rest); both approach `√(k/c)` because `ρ` cancels at terminal speed |

No ship, Bukkit, or command tests in this slice.

## Acceptance

- `./gradlew check` is green.
- `ThrustForce` behavior is unchanged.
- One-arg `QuadraticDragForce` behavior is unchanged.
- A watercraft composition (liquid medium) and an airship composition (uniform air density) can attach the same two types and differ only by the `DensityField`.
