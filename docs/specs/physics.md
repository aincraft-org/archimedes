# Physics — Living Spec

> Status: active
> Last updated: 2026-08-19
> Owners: jlo

The `dev.mintychochip.phys` package is a small, generic, Bukkit-independent rigid-body physics library. The Archimedes ship mechanics are one client of this library; `dev.mintychochip.archimedes.phys` contains the ship-specific forces, surfaces, and configuration.

Success looks like: any domain can create a `Body`, attach `Collider`s and `Force`s, and step it through a `Physics` engine with a `World` without importing Bukkit or ship types. Vehicle “types” (watercraft, airship, airplane) are force lists, not subclasses. The ship client uses the generic core for its Y-only buoyancy and the approved mass model.

## Boundaries

### In scope

- Generic math helpers on JOML (`Vectors`, `Quaternions`) plus `Transform`.
- Generic abstractions: `Body`, `Collider`, `Shape`, `Material`, `Force`, `World`, `FluidField`, `DensityField`, `FlowField`, `Physics`.
- Default `BodyImpl`, `Aabb`, `ColliderImpl`, `Octree`, and `PhysicsEngine` in `:common`.
- Octree broadphase plus AABB contact depenetration on `Physics.step` (body–body).
- Reusable `Force` units: `GravityForce`, `FluidBuoyancyForce`, `QuadraticDragForce` (optional `DensityField`), `ThrustForce`, `MediumThrustForce`, `LiftForce`, `PressureSailForce`, `SupportForce`, `CoulombFrictionForce`, `ViscousDragForce`, `AngularDragForce`, `VegetationDragForce`.
- Ship client (`dev.mintychochip.archimedes.phys`):
  - `ShipBody` / `VehicleFactory` construction from `Vehicle` blocks.
  - `MaterialKeyResolver` for mapping `ShipBlock.blockData()` to canonical material keys.
  - `ShipMassModel` with per-material density table and tracked-player rider mass.
  - `ShipBuoyancyForce` (waterline lift only) plus `GravityForce` on the generic step.
  - `EnvelopeBuoyancyForce` (envelope-cell aerostatic lift; factory only until the live tick uses `VehicleFactory`).
  - `ShipSails` maps marked structure blocks to `PressureSailForce`.
  - `ShipPhysics` facade that steps the engine and drives `ShipRuntime`.

### Out of scope / non-goals

- Rotational Archimedes ship gameplay (yaw/pitch/roll). `ShipPose` is now `x,y,z`; yaw is still out.
- In-game Minecraft airships/airplanes (commands, rendering, 6DOF `ShipRuntime`, player controls, fuel).
- Full flight-sim aerodynamics (stall, control surfaces, stability derivatives, atmosphere tables).
- Restitution, joints, constraint/LCP solvers, and replacing Minecraft Shulker hulls for player-solid ships.
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
- Ships do not run a parallel equilibrium search. Pose changes come from `Physics.step`.
- All-or-nothing moves and `ShipRuntime` rollback are preserved.
- Configuration for material densities and tolerances is validated at plugin enable; unknown runtime materials fall back to a configured default.

## Implementation guidance

- Prefer records or small final classes for math and state types. Vectors/quaternions are JOML (`Vector3dc`, `Quaterniondc`); `Transform` wraps them.
- Keep `PhysicsEngine` deterministic and unit-testable without a server. Orientation integration uses JOML `Quaterniond.integrate(dt, ωx, ωy, ωz)` and then `normalize()`. JOML's Taylor path can leave `|q|` off by ~1e-8, which fails `Transform`'s `1e-9` unit check and aborts the whole ship tick.
- Put reusable vehicle laws in standalone `Force` units the caller attaches. Do not add watercraft/airship/airplane subclasses of `Body`.
- Parameterize hydrostatic buoyancy by `DensityField` so water and air share `F = −ρV g`. Watercraft uses `DensityField.liquid(FluidField)` (gated on `isFluid`). Airships use `DensityField.uniform(ρ_air)`.
- `ShipBuoyancyForce` is waterline lift only. `ShipPhysics` attaches `GravityForce` + `ShipBuoyancyForce` and steps `Physics`. Do not add a second integrator or Y-search solver.
- Lift is `c · |v × n|²` along the body-local lift axis: ~0 at rest, grows with airspeed, ignores purely vertical motion.
- `MediumThrustForce` is `F = k · ρ(p) · n̂` at a body-local application point, with `τ = (p − X) × F`. The no-medium constructor uses `DensityField.liquid(world.fluidField())`. It never gates on `isFluid` directly.
- `ThrustForce` stays density-blind (rocket). `QuadraticDragForce(c)` stays lumped `−c |v| v` and does not sample density. `QuadraticDragForce(c, DensityField)` is `−c · ρ · |v| v` with volume-weighted mean `ρ` over colliders (body position if none). The one-arg constructor is not a world-liquid default.
- Parameterize medium thrust and density drag by `DensityField` the same way as hydrostatic buoyancy. Watercraft uses liquid; airships use `DensityField.uniform(ρ_air)`. Do not add `World.densityField()`.
- `FlowField` is pointwise flow velocity (`still`, `uniform`, axis-aligned `box`, and `compose`). Multiple winds exist by composing or by attaching different fields to different forces. Do not add `World.flowField()`. Do not use `isFluid` for air.
- `PressureSailForce` is one-sided cloth: `F = q A max(n̂ · v̂_app, 0)² n̂`, `v_app = v_wind(p) − v − ω × r`, `τ = r × F`. Density and wind are constructor-injected. Still air or edge-on sheet is zero force.
- Hook sails to a structure with `ShipSails.forces`. `ShipPhysics.tick` attaches cloth (`*_wool`, `*_banner`, `*_wall_banner`) plus lumped air drag when a `FlowField` is supplied. The plugin default wind is `+Z`. `ShipPose` stores `x,y,z`; yaw is still out.
- Support and Coulomb friction share a `ContactPlane`. Support cancels the compressive gravity load along the normal when the body is in contact; it is not a penetration constraint. Friction uses that same gravity-derived `N`: kinetic is `−μ_k N v̂_t`; static cancels sibling tangent load when `|T| ≤ μ_s N` at rest. `N = 0` off the plane.
- Viscous linear drag is `F = −c v`, distinct from quadratic `−c |v| v`. Angular drag is `τ = −c ω`. Neither is applied to Archimedes ships.
- Put Bukkit-specific material/world parsing behind interfaces and implement them only in `:paper`.
- Gate ship physics on `World.isChunkLoaded`. Bukkit implementation must call `org.bukkit.World#isChunkLoaded` (Paper: `ServerChunkCache` / `ChunkMap.getUpdatingChunkIfPresent`). Never `getChunkAt` or `loadChunk` for this probe.
- The ship client is responsible for turning a `Ship` into a `Body` and for driving `ShipRuntime` after integration.
- Reuse existing `ShipRuntime` transaction semantics; do not re-implement rollback in physics.
- Write tests for the generic core first, then the ship client. Composition tests must call real `Force.apply` and `Physics.step`. Every shipped catalog force must also have a real-step assertion, not `apply` alone.
- `Physics.step` is the only integrator. It sums attached results, skips inactive bodies, and does not inject gravity. After integration it runs an octree broadphase and AABB depenetration for bodies that have colliders. Coulomb friction may re-invoke sibling `apply` to read tangent load; those sibling results are still summed once by the step.
- Minecraft player-solid ship hulls stay Shulker volumes. The octree is the generic engine broadphase, not a replacement for those hulls.

## Current

- [x] Generic math on JOML (`Vectors`, `Quaternions`, `Transform`).
- [x] Generic core API (`Body`, `Collider`, `Shape`, `Material`, `Force`, `World`, `FluidField`, `Physics`).
- [x] Default `BodyImpl` and `Aabb` shape.
- [x] `PhysicsEngine` with semi-implicit Euler 6DOF integration (no gravity injection).
- [x] Ship client: `ShipBody`, `MaterialKeyResolver`, `ShipMassModel`, `ShipBuoyancyForce`, `ShipPhysics` on `Physics.step`.
- [x] Removed `EquilibriumSolver` / `EquilibriumResult`; mass/draft acceptance is asserted via the real step.
- [x] `:paper` adapters (`BukkitFluidField`, `BukkitMaterialKeyResolver`, config loading).
- [x] Migrate existing `dev.mintychochip.phys.Buoyancy*` into `dev.mintychochip.archimedes.phys`.
- [x] Acceptance A1–A20 from the approved mass model.
- [x] `DensityField` separate from `FluidField.isFluid` so air density is not ship water.
- [x] Reusable forces: gravity, fluid buoyancy, quadratic drag, directed thrust, airspeed-dependent lift.
- [x] Watercraft / airship / airplane compositions on the real engine (net-up liquid, aerostatic hover at rest, lift ~0 at rest and larger with airspeed).
- [x] `ContactPlane` + `SupportForce` (gravity-derived normal load; no through-plane acceleration when composed with gravity).
- [x] Coulomb friction (kinetic opposes slip, zero with no load, static hold vs slip).
- [x] Viscous linear drag and angular drag (rest ≈ 0; `|v|` / `|ω|` decrease after `step`).
- [x] Every catalog force has a defining post-step signature through the real `Physics.step`, including a mixed collection (vehicles + floor contact + spin).
- [x] Octree broadphase + AABB contact depenetration on `Physics.step`.
- [x] `FlowField`: still / uniform / box / compose; not owned by `World`.
- [x] `PressureSailForce`: rest-in-breeze moves; still air and edge-on sheet do not; offset yaws.
- [x] `ShipSails` maps marked structure blocks (`facing=` normal, 1 m² each) to pressure sails.
- [x] `ShipPhysics.tick` integrates sail XZ into `ShipPose(x,y,z)` when cloth faces the wind.
- [x] `ShipPhysics` skips tick/rise/sink when any ship chunk is unloaded (`World.isChunkLoaded`). Bukkit uses `org.bukkit.World#isChunkLoaded` (ServerChunkCache map lookup), not `getChunkAt`.
- [x] Kelp/seagrass are passable vegetation (`World.vegetation`); they drag via `VegetationDragForce` and do not fail path clearance.
- [x] Review: same `PressureSailForce` drives watercraft and airship compositions; ship client is still waterline-only (dry cloth falls).
- [x] `QuadraticDragForce(c, DensityField)` is `−c · ρ · |v| v`; the one-arg constructor stays lumped. `ShipPhysics` attaches the density form as `WaterDrag` (`DensityField.liquid`) plus a small lumped air `Drag` with sails.
- [x] Default plugin gravity is `10` blocks/s² (same scale as the generic engine). `0.05`/`0.5` plus 0.9 damping made airborne sails glide.
- [x] Plugin hull/water densities are ~10× the old `water=1` table (`water=10`, oak `6`, log `7`, wool `1`, default `10`) so weight exceeds rest sail force and a wooden deck can still float the cloth.
- [x] `PhysicsEngine` renormalizes orientation after `integrate` so a slightly non-unit JOML quaternion cannot abort ship ticks.
- [x] Standing riders are teleported by the ship's pose delta. A normal jump stays on the deck; the solid hull follows the fractional pose.
- [x] Wet-cell fraction drives displacement, so a large deck sits in the water and a boarded player deepens draft.
- [x] `MediumThrustForce` samples `DensityField` at a body-local point and produces `r × F` torque.
- [x] `QuadraticDragForce` optional `DensityField` overload; one-arg lumped law unchanged.
- [x] Catalog/field inventory (2026-08-19): 12 phys `Force`s + `ShipBuoyancyForce` + `EnvelopeBuoyancyForce`; `World` has gravity/fluid/timeStep/obstacle/chunk/vegetation only. Proof: `CatalogAndFieldInventoryTest`.

### Current notes

- Dated design: `docs/superpowers/specs/2026-08-16-physics-library-design.md` (math types later moved to JOML).
- Medium thrust / density drag contract: `docs/superpowers/specs/2026-08-17-medium-propulsion-design.md`.
- Sail ideas (not an implementation contract): `docs/superpowers/specs/2026-08-17-sail-propulsion-ideas.md`.
- Ships / airships / sail-aesthetics review: `docs/superpowers/specs/2026-08-17-physics-models-review.md`.
- `docs/specs/buoyancy.md` remains the ship-client vertical contract.
- Generic 6DOF is available to any caller. Archimedes `ShipPose` stores `x,y,z`; yaw is still out. `ShipBody` is rebuilt at identity orientation every tick, so sail torque never becomes heading.
- `archimedes.phys` review (2026-08-17): `Body` is the per-step physics object. `ShipPhysics` is the ship facade (rebuild body, clamp, path, `ShipRuntime`). `RiderCount` and `MaterialKeyResolver` are one-method seams so `:common` stays off Bukkit; they are not unused. No type in that package is a pass-through.
- Propulsion split from the 2026-08-17 review: watercraft stay liquid buoyancy + sails (lifting sails later); airships should hover on envelope/`FluidBuoyancy(uniform ρ_air)` and drive with `MediumThrustForce`. Pressure sails on airships are optional flavor, not the look.
- Accuracy inventory 2026-08-19: drag uses body `v`, not `v − u_flow` (`FlowField` is sail-only; `FluidField`/`World` have no current). `BodyImpl` inertia is isotropic `m I`. `PhysicsEngine` has no `ω × Iω`. Waterline and envelope lift apply zero torque. `LiftingSailForce` is not on the classpath. `VehicleFactory` can attach envelope + engines; `ShipPhysicsImpl.tick` still does not.

## Next

- [x] `MediumThrustForce`: density-scaled actuator at a body-local point with `r × F` torque
- [x] `QuadraticDragForce(c, DensityField)`: `−c · ρ · |v| v`; one-arg constructor stays lumped
- [x] Water drag on ships via density-scaled `QuadraticDragForce` (air keeps a small lumped drag).
- [ ] Relative-flow drag: `F = −c ρ |v_app| v_app` with `v_app = v − u_flow`. Currents/wind on drag via injected `FlowField` or `FluidField` velocity — not `World.densityField()`.
- [ ] Anisotropic inertia from collider AABBs (`BodyImpl` is still `I = m I`).
- [ ] Gyroscopic `ω × Iω` in `PhysicsEngine` (no-op until inertia is anisotropic).
- [ ] `LiftingSailForce`: angle-of-attack lift/drag catalog unit.
- [ ] Keel / lateral-resistance catalog unit (opposes sideslip).
- [ ] Horizontal movement and steering for ships beyond sail-driven XZ.
- [ ] `PhysicsWorld` that owns and steps a collection of bodies.
- [ ] More `Shape` primitives (sphere, capsule).

## Future

- [ ] Propulsion, steering, yaw wired into Archimedes ship gameplay.
- [ ] Ship-vs-ship and terrain collision.
- [ ] Multi-ship fluid displacement.
- [ ] Water entry/splash effects and drowning rules.
- [ ] Stall, control surfaces, and atmosphere tables (`ρ(z)` as a `DensityField`, not `World.densityField()`).
- [ ] Attach `AngularDragForce` once vehicle yaw is retained.
- [ ] Buoyancy torque `r_cb × F` (waterline and envelope lift are still τ = 0).
- [ ] Added mass / virtual mass in fluid.
- [x] Attach structure sails on `ShipPhysics.tick` and integrate `ShipPose` x/z
- [x] Rider carry along XZ (same best-effort carrier as Y; players get velocity, others teleport)
- [ ] Yaw / rotational ship pose

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | `dev.mintychochip.phys` becomes a generic library; ships are a client | User directive |
| 2026-08-16 | Minimal interface core (Body/Collider/Force/World) | Simple, testable, evolvable |
| 2026-08-16 | Primitive colliders (`Aabb` first) for geometry | Maps to ship blocks while staying generic |
| 2026-08-16 | Material/world parsing stays in `:paper`; core is Bukkit-free | Reusability and testability |
| 2026-08-16 | `Physics` is stateless: `step(World, Collection<Body>)`; body lists are unmodifiable | Deterministic, testable, no hidden ownership |
| 2026-08-16 | `Force.apply` uses `World.timeStep()`; no extra `dt` parameter | Avoids redundant timestep arguments |
| 2026-08-16 | `EquilibriumSolver` returns a target pose `y`; integration chases it with damping/clamps | Later reversed — see 2026-08-17 |
| 2026-08-16 | New density/tolerance keys live under the existing `buoyancy:` section | Keeps related buoyancy config together |
| 2026-08-16 | Custom `Vector3`/`Quaternion`/`Matrix3x3` records replaced by JOML | One vector/quat type; less conversion |
| 2026-08-17 | `DensityField` is independent of `FluidField.isFluid` | Air density for lift/airships must not make empty air count as ship water |
| 2026-08-17 | Vehicle types are force lists, not `Body` subclasses | Same integrator; water/air/airplane differ only by attached forces |
| 2026-08-17 | `Quaterniond.integrate(dt, ωx, ωy, ωz)` (JOML argument order) | Swapped args left yaw-only torque with unchanged orientation |
| 2026-08-17 | Promote attachable support, Coulomb friction, and viscous/angular drag into the generic library | User asked for a complete attachable force catalog; not a contact/LCP solver |
| 2026-08-17 | Catalog forces are proven through `Physics.step`, not `apply` alone | Isolated apply tests can pass while the integrator never sees the force |
| 2026-08-17 | Remove ship `EquilibriumSolver`; ships only step gravity + waterline buoyancy | Duplicate Y-search fought the engine and is not needed for draft |
| 2026-08-17 | Body–body collision uses an octree broadphase and AABB depenetration | User asked for spatial-tree collision; not an LCP solver or Shulker replacement |
| 2026-08-17 | Keep `ShipPhysics` and `RiderCount`; do not fold them into `Body` | `Body` is ephemeral per step; rider count and runtime moves are ship/Bukkit seams |
| 2026-08-17 | Generic `MediumThrustForce` + opt-in density drag; no ship wiring | Same catalog later attaches to airships; `isFluid` must not kill air props |
| 2026-08-17 | `ThrustForce` remains density-blind; one-arg quadratic drag stays lumped | Rocket and legacy callers keep their law; density coupling is explicit |
| 2026-08-17 | Sails are future catalog forces, not medium thrust and not a `Body` subclass | Need `v_app` and area/orientation; `F = k ρ n̂` has no relative wind |
| 2026-08-17 | `FlowField` is constructor-injected; multiple winds via `compose` or per-force fields | Same reason as no `World.densityField()`: two bodies can share a world and differ by attached flow |
| 2026-08-17 | `PressureSailForce` is one-sided `q A` cloth; structure hook is `ShipSails` | User asked to wire sails and attach them to the build; not `ShipPhysics` 6DOF |
| 2026-08-17 | `ShipPhysics.tick` applies sail XZ into `ShipPose`; default plugin wind is `+Z` | User: ships still did not move forward after the catalog-only sail |
| 2026-08-17 | Catalog already allows watercraft and airship propulsion via force lists; plugin is still water ships | Same `PressureSailForce` + different buoyancy; `ShipBuoyancyForce` is waterline-only |
| 2026-08-17 | Pressure sails are honest cloth and a weak airship aesthetic | One-sided `q A` cannot go upwind; wool defaults to `+Z`; no yaw; props want `MediumThrust` |
| 2026-08-17 | Physics is skipped unless ship chunks are in the loaded-chunk cache | Unloaded `getBlockAt` would load from disk; `isChunkLoaded` is a cache probe |
| 2026-08-17 | Seaweed is vegetation drag, not a path block | Kelp/seagrass are not hull solids; `F = −c σ |v| v` |
| 2026-08-17 | Ship water drag is density-scaled; default gravity is `0.5` | Lumped drag made water and air feel the same; `g=0.05` plus damping hid airborne fall |
| 2026-08-17 | Default gravity is `10` blocks/s² | `g=0.5` plus 0.9/tick damping terminals at ~0.2 blk/s, so sails still look like a glide |
| 2026-08-17 | Plugin water/hull densities scale to `10`/`6` | Sail `qA` is SI-ish; old `water=1` hulls were ~47 mass and lighter than the sail force |
| 2026-08-17 | Cloth is light; `rise` damps each step; buoyancy uses wet-cell density | Wool at water density plus 80 undamped rise steps slammed a surface spawn ~11 blocks down |
| 2026-08-17 | Blocked Y no longer rejects sail XZ | `g=10` made `floor(y)` drop into the seafloor; all-or-nothing path froze ships |
| 2026-08-17 | Horizontal slides ignore keel solids; grass is passable | Overlapping sand/grass at the deck froze every move, including XZ |
| 2026-08-17 | `PhysicsEngine` renormalizes after `integrate` | Live ticks threw `quaternion must be normalized` on plugin-scale small sails (JOML Taylor band) and aborted every ship |
| 2026-08-17 | Rider carry applies the full pose delta | Sail XZ left standing players behind; carry used to no-op when `dy = 0` |
| 2026-08-17 | Displacement is the wet fraction of each cell | A barely-wet deck was counted fully, so large rafts sat on the water and player load did not bob |
| 2026-08-19 | Accuracy gaps ranked as relative-flow drag, anisotropic I, gyro, `LiftingSailForce`, keel — not new `World` fields | Inventory of 14 `Force` types; `FlowField` already exists for sails; drag still ignores it |

## Open questions

- [x] Can the shipped catalog drive both ships and airships? Yes, as force lists. Proof: `VehiclePropulsionCompositionTest`.
- [x] Will pressure sails look right as the main airship drive? No. Downwind plate + no heading. Use aerostatic hover + props for that look; keep cloth as flavor or wait for `LiftingSailForce`.
