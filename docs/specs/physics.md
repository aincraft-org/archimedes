# Physics — Living Spec

> Status: active
> Last updated: 2026-08-17
> Owners: jlo

The `dev.mintychochip.phys` package is a small, generic, Bukkit-independent rigid-body physics library. The Archimedes ship mechanics are one client of this library; `dev.mintychochip.archimedes.phys` contains the ship-specific forces, surfaces, and configuration.

Success looks like: any domain can create a `Body`, attach `Collider`s and `Force`s, and step it through a `Physics` engine with a `World` without importing Bukkit or ship types. Vehicle “types” (watercraft, airship, airplane) are force lists, not subclasses. The ship client uses the generic core for its Y-only buoyancy and the approved mass model.

## Boundaries

### In scope

- Generic math helpers on JOML (`Vectors`, `Quaternions`) plus `Transform`.
- Generic abstractions: `Body`, `Collider`, `Shape`, `Material`, `Force`, `World`, `FluidField`, `DensityField`, `Physics`.
- Default `BodyImpl`, `Aabb`, `ColliderImpl`, and `PhysicsEngine` in `:common`.
- Reusable `Force` units: `GravityForce`, `FluidBuoyancyForce`, `QuadraticDragForce`, `ThrustForce`, `LiftForce`, `SupportForce`, `CoulombFrictionForce`, `ViscousDragForce`, `AngularDragForce`.
- Ship client (`dev.mintychochip.archimedes.phys`):
  - `ShipBody` construction from `Ship` blocks.
  - `MaterialKeyResolver` for mapping `ShipBlock.blockData()` to canonical material keys.
  - `ShipMassModel` with per-material density table and tracked-player rider mass.
  - `ShipBuoyancyForce` using a `FluidField` (vertical net force; unchanged by generic compositions).
  - `EquilibriumSolver` (bounded, monotonicity-guarded, force-balance).
  - `ShipPhysics` facade that drives `ShipRuntime`.

### Out of scope / non-goals

- Horizontal or rotational Archimedes ship gameplay (`ShipPose` remains a scalar `y`).
- In-game Minecraft airships/airplanes (commands, rendering, 6DOF `ShipRuntime`, player controls, fuel).
- Full flight-sim aerodynamics (stall, control surfaces, stability derivatives, atmosphere tables).
- Collision detection, contact manifolds, restitution, joints, constraint/LCP solvers, ship-vs-ship or terrain contact solving.
- Springs, rolling resistance, and gyroscopic `ω × Iω` beyond the current integrator.
- Multi-ship water displacement or a shared fluid simulation.
- Water entry effects, drowning, damage.
- A formal vehicle-type registry or plugin API for “create airplane”.

## Invariants

- `dev.mintychochip.phys` must not depend on Bukkit, `Ship`, `BlockData`, or any Minecraft-specific type.
- `dev.mintychochip.archimedes.phys` is the only consumer of `Ship` and `ShipRuntime` in the physics layer.
- A `Body` is constructed with its `Collider`s and `Force`s; both lists are unmodifiable for the lifetime of the body.
- `Physics` does not retain bodies between calls; it steps a caller-supplied `Collection<Body>`.
- `PhysicsEngine` is vehicle-blind: it sums attached `Force.Result` values and integrates linear and angular state. It does not inject gravity or inspect ship types.
- `FluidField.isFluid` means ship-submerging liquid only. Atmosphere is queried through `DensityField`, never by setting `isFluid` true for air.
- `ShipPose.anchorDy() = floor(y)` remains authoritative for block restoration, collision, and persistence.
- No `ships.json` schema changes.
- No-equilibrium is a diagnostic, not an exception.
- All-or-nothing moves and `ShipRuntime` rollback are preserved.
- Configuration for material densities and tolerances is validated at plugin enable; unknown runtime materials fall back to a configured default.

## Implementation guidance

- Prefer records or small final classes for math and state types. Vectors/quaternions are JOML (`Vector3dc`, `Quaterniondc`); `Transform` wraps them.
- Keep `PhysicsEngine` deterministic and unit-testable without a server. Orientation integration uses JOML `Quaterniond.integrate(dt, ωx, ωy, ωz)`.
- Put reusable vehicle laws in standalone `Force` units the caller attaches. Do not add watercraft/airship/airplane subclasses of `Body`.
- Parameterize hydrostatic buoyancy by `DensityField` so water and air share `F = −ρV g`. Watercraft uses `DensityField.liquid(FluidField)` (gated on `isFluid`). Airships use `DensityField.uniform(ρ_air)`.
- Keep `ShipBuoyancyForce` as the ship client's vertical net force so existing equilibrium/bobbing does not depend on the new compositions.
- Lift is `c · |v × n|²` along the body-local lift axis: ~0 at rest, grows with airspeed, ignores purely vertical motion.
- Support and Coulomb friction share a `ContactPlane`. Support cancels the compressive gravity load along the normal when the body is in contact; it is not a penetration constraint. Friction uses that same gravity-derived `N`: kinetic is `−μ_k N v̂_t`; static cancels sibling tangent load when `|T| ≤ μ_s N` at rest. `N = 0` off the plane.
- Viscous linear drag is `F = −c v`, distinct from quadratic `−c |v| v`. Angular drag is `τ = −c ω`. Neither is applied to Archimedes ships.
- Put Bukkit-specific material/world parsing behind interfaces and implement them only in `:paper`.
- The ship client is responsible for turning a `Ship` into a `Body` and for driving `ShipRuntime` after integration.
- Reuse existing `ShipRuntime` transaction semantics; do not re-implement rollback in physics.
- Write tests for the generic core first, then the ship client. Composition tests must call real `Force.apply` and `Physics.step`.

## Current

- [x] Generic math on JOML (`Vectors`, `Quaternions`, `Transform`).
- [x] Generic core API (`Body`, `Collider`, `Shape`, `Material`, `Force`, `World`, `FluidField`, `Physics`).
- [x] Default `BodyImpl` and `Aabb` shape.
- [x] `PhysicsEngine` with semi-implicit Euler 6DOF integration (no gravity injection).
- [x] Ship client: `ShipBody`, `MaterialKeyResolver`, `ShipMassModel`, `ShipBuoyancyForce`, `EquilibriumSolver`, `ShipPhysics`.
- [x] `:paper` adapters (`BukkitFluidField`, `BukkitMaterialKeyResolver`, config loading).
- [x] Migrate existing `dev.mintychochip.phys.Buoyancy*` into `dev.mintychochip.archimedes.phys`.
- [x] Acceptance A1–A20 from the approved mass model.
- [x] `DensityField` separate from `FluidField.isFluid` so air density is not ship water.
- [x] Reusable forces: gravity, fluid buoyancy, quadratic drag, directed thrust, airspeed-dependent lift.
- [x] Watercraft / airship / airplane compositions on the real engine (net-up liquid, aerostatic hover at rest, lift ~0 at rest and larger with airspeed).
- [x] `ContactPlane` + `SupportForce` (gravity-derived normal load; no through-plane acceleration when composed with gravity).
- [x] Coulomb friction (kinetic opposes slip, zero with no load, static hold vs slip).
- [x] Viscous linear drag and angular drag (rest ≈ 0; `|v|` / `|ω|` decrease after `step`).

### Current notes

- Dated design: `docs/superpowers/specs/2026-08-16-physics-library-design.md` (math types later moved to JOML).
- `docs/specs/buoyancy.md` remains the ship-client vertical contract.
- Generic 6DOF is available to any caller; Archimedes `ShipPose` is still Y-only.

## Next

- [ ] Horizontal movement and water drag for ships.
- [ ] `PhysicsWorld` that owns and steps a collection of bodies.
- [ ] More `Shape` primitives (sphere, capsule).

## Future

- [ ] Propulsion, steering, yaw wired into Archimedes ship gameplay.
- [ ] Ship-vs-ship and terrain collision.
- [ ] Multi-ship fluid displacement.
- [ ] Water entry/splash effects and drowning rules.
- [ ] Stall, control surfaces, and atmosphere tables.

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | `dev.mintychochip.phys` becomes a generic library; ships are a client | User directive |
| 2026-08-16 | Minimal interface core (Body/Collider/Force/World) | Simple, testable, evolvable |
| 2026-08-16 | Primitive colliders (`Aabb` first) for geometry | Maps to ship blocks while staying generic |
| 2026-08-16 | Material/world parsing stays in `:paper`; core is Bukkit-free | Reusability and testability |
| 2026-08-16 | `Physics` is stateless: `step(World, Collection<Body>)`; body lists are unmodifiable | Deterministic, testable, no hidden ownership |
| 2026-08-16 | `Force.apply` uses `World.timeStep()`; no extra `dt` parameter | Avoids redundant timestep arguments |
| 2026-08-16 | `EquilibriumSolver` returns a target pose `y`; integration chases it with damping/clamps | Matches existing damped bobbing contract |
| 2026-08-16 | New density/tolerance keys live under the existing `buoyancy:` section | Keeps related buoyancy config together |
| 2026-08-16 | Custom `Vector3`/`Quaternion`/`Matrix3x3` records replaced by JOML | One vector/quat type; less conversion |
| 2026-08-17 | `DensityField` is independent of `FluidField.isFluid` | Air density for lift/airships must not make empty air count as ship water |
| 2026-08-17 | Vehicle types are force lists, not `Body` subclasses | Same integrator; water/air/airplane differ only by attached forces |
| 2026-08-17 | `Quaterniond.integrate(dt, ωx, ωy, ωz)` (JOML argument order) | Swapped args left yaw-only torque with unchanged orientation |
| 2026-08-17 | Promote attachable support, Coulomb friction, and viscous/angular drag into the generic library | User asked for a complete attachable force catalog; not a contact/LCP solver |

## Open questions

None currently.
