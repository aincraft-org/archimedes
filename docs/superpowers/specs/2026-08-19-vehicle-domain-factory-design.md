# Vehicle Domain and Factory

> **Status:** Draft for review. Not an implementation contract until approved.
> **Related:** `docs/specs/ship-model.md`, `docs/specs/physics.md`, `docs/specs/ship-runtime.md`,
> `docs/specs/commands.md`, `docs/specs/buoyancy.md`
> **Date:** 2026-08-19

## Goal

Replace `Ship` with `Vehicle` as the only plugin domain type. A factory turns the captured hull
into the physics aggregate: one mass, one pose, one force list for the whole vehicle. Watercraft
and airships are not kinds. They are the same vehicle in the same physics. Parts (sails, turbines,
engines, stacked deck cargo) always contribute mass. Actuator flags only enable thrust.

Player command stays `/arch`. Persistence stays `archimedes.json`.

## Scope

### In scope

- Rename the persisted/runtime domain type `Ship` → `Vehicle` in `:api`, `:common`, and `:paper`
  (model, service, runtime, physics facade, store, targeting).
- `VehicleFactory` that builds a `Body` from a `Vehicle` each tick.
- Intrinsic buoyancy: waterline lift from hull cells in liquid; aerostatic lift from envelope
  cells in air. Not a kind. Not inferred as “this is an airship.”
- Sail and engine enable flags on `Vehicle` (default on). Flags gate actuator forces only.
- Config material lists for sails (already cloth), engines, and envelope.
- `/arch` subcommands to furl/unfurl sails and start/stop engines. State is in-memory this spec.
- Tests through `Physics.step` for mass-always-counts, dual lift, and actuator gating.

### Out of scope

- Renaming the player command to `/vehicle`. `/arch` is the command; `/ship` remains the alias.
- Schema version bump, `kind` field, or persisted sail/engine flags.
- Live cargo after assembly (placed blocks, dropped items, extra entities besides tracked
  riders). Stacked stuff counts when it was part of the captured hull.
- Yaw / retained orientation. `Vehicle` pose stays `x,y,z`. Body is rebuilt at identity
  orientation each tick.
- `LiftingSailForce`, fuel, throttle UI, marker-block controllers.
- Wind-harvesting turbines as a second law. Turbines and engines are `MediumThrustForce`.
- Replacing Shulker hulls, GPU meshes, or the generic physics catalog.

## Vocabulary

| Term | Meaning |
|------|---------|
| **Vehicle** | Long-lived domain object. Hull data, pose, actuator flags. Physics is applied to this whole object. |
| **Part** | A captured block. It has mass. It may also be cloth, engine, or envelope. It is not a simulated body. |
| **VehicleFactory** | Walks the hull and builds an ephemeral `Body` (colliders + forces) for one step. |
| **Body** | Generic rigid body from `dev.mintychochip.phys`. Unmodifiable force list. Rebuilt every tick. |
| **Actuator flag** | `sailsEnabled` / `enginesEnabled`. Default true. Missing parts cannot produce force. |

There is no `kind` enum and no `Airship` subclass.

## Architecture

```text
/arch assemble
    → scan connected blocks
    → Vehicle (id, owner, origin, blocks, pose, flags)
    → persist archimedes.json
    → runtime spawn (displays + hulls)

tick:
    Vehicle
    → chunks loaded?
    → VehicleFactory.buildBody(vehicle, world, riders, flags)
    → Physics.step
    → write pose onto Vehicle
    → runtime.move
    → on runtime failure, restore pose
```

`Vehicle` owns policy (which actuators are on). `Body` is the per-step physics object. Do not
retain a `Body` on the vehicle. Do not attach forces to individual parts.

Code APIs speak `Vehicle`. Player surface speaks `/arch`.

## Vehicle

Package: `dev.mintychochip.archimedes.model.Vehicle` (replaces `Ship`).

Fields:

- `id`, `ownerId`, `origin`, `blocks` (immutable list, same `VehicleBlock` snapshots as today’s
  `ShipBlock`)
- `pose` (`x,y,z`, same `ShipPose` semantics; rename to `VehiclePose` with the type)
- `physicsEnabled` (today’s `buoyancyEnabled`; `/arch buoyancy` kill switch)
- `sailsEnabled` (default `true`, not persisted)
- `enginesEnabled` (default `true`, not persisted)

Load from `archimedes.json` with the existing optional-field compat (`pose` missing → `0`,
physics missing → enabled). Missing actuator flags → `true`. Save does not write actuator flags.

`Vehicle.blocks` is still immutable after construction. You cannot add mass by placing blocks on
an assembled vehicle. You add mass by capturing those blocks at assemble time (a stack on the
deck that is part of the scanned component).

## Mass

Every captured block adds mass. No exceptions for cloth, engines, envelope, or extra blocks
stacked on the deck. If it was in the scan, it weighs. Actuator flags never subtract mass.

```text
mass = Σ density(block)  +  riderCount × playerMass
```

- Furl sails: cloth colliders and mass stay; `PressureSailForce` is omitted.
- Stop engines: engine colliders and mass stay; `MediumThrustForce` is omitted.
- Unknown materials use the configured default density.
- Tracked standing players already add rider mass. That is not a substitute for hull cargo.

## VehicleFactory

`dev.mintychochip.archimedes.phys.VehicleFactory`

```text
Body buildBody(Vehicle vehicle, World world, int riders, DensityField air, FlowField wind)
```

One pass over `vehicle.blocks()`:

1. Each block → unit AABB collider at local block center, material density from config.
   Total mass from `VehicleMassModel` (rename of `ShipMassModel`).
2. Always attach `GravityForce` when `physicsEnabled`.
3. Always attach waterline lift (`ShipBuoyancyForce` renamed to a vehicle waterline force)
   when `physicsEnabled`. Liquid is still `FluidField.isFluid`. Dry air gives zero waterline lift.
4. If any block’s material is in the envelope set, attach envelope lift **from those cells
   only**. Law is still `F = −ρ_air V_envelope g`. Do not attach generic
   `FluidBuoyancyForce(uniform air)` to the whole body — that would count oak decks as gas.
   Intrinsic: no flag.
5. If `sailsEnabled` and cloth exists, attach `ShipSails` / `PressureSailForce` as today.
6. If `enginesEnabled` and engine blocks exist, attach `MediumThrustForce` at each engine
   block center, axis from `facing=` (default `+Z`), coefficient from config.
7. Keep current water density-scaled drag and lumped air drag with sails, vegetation drag,
   and the existing path/chunk gates.

Factory does not classify the vehicle as watercraft or airship. If the hull has envelope cells
and is in empty air, aerostatic lift is on the body. If it is in liquid, waterline lift is on
the body. Both can be attached at once.

### Part detection

Config lists, same pattern as today’s cloth keys:

| System | Detection | Default direction |
|--------|-----------|-------------------|
| Sails | existing cloth (`*_wool`, `*_banner`, `*_wall_banner`) | `facing=`, else `+Z` |
| Engines / turbines | `engine-materials` in config | `facing=`, else `+Z` |
| Envelope | `envelope-materials` in config, **disjoint from sail keys** | n/a |

Wool stays sails. A wool wall is not a gasbag. Envelope defaults to a small distinct set
(`minecraft:slime_block`, `minecraft:honey_block`) so an ordinary sailed hull does not hover
the moment it leaves the water. Empty envelope set → no aerostatic term.

Engine defaults: `minecraft:furnace`, `minecraft:blast_furnace`, `minecraft:smoker`. Coefficient
is one config scalar (`engine-thrust`, finite ≥ 0).

## Commands

`/arch` is the command. `/ship` stays an alias. No `/vehicle`.

| Command | Behavior |
|---------|----------|
| existing assemble / inspect / disassemble / kill / sink / sail / buoyancy | Same targeting and permissions; services take `Vehicle` |
| `/arch sails` | Toggle `sailsEnabled` on the nearby hull (same targeting as inspect) |
| `/arch engines` | Toggle `enginesEnabled` on the nearby hull |

Toggles persist only in memory until restart (then default on). Inspect reports whether sails
and engines are on, and still lists each attached force.

`/arch buoyancy` remains a whole-vehicle physics enable, not a kind and not water-vs-air.

## Persistence and runtime

- File remains `archimedes.json`. Field names stay (`id`, `owner`, `origin`, `blocks`, `pose`,
  `buoyancy`). Java mapping is `Vehicle`.
- Runtime spawn/move/remove still uses displays + Shulker hulls + rider carry. Type names in
  code become `VehicleRuntime` and friends; behavior does not change in this spec.
- Pose write-back and all-or-nothing rollback stay.

## Errors

- Services return reason-only failures. `/arch` owns the prefix.
- Unloaded chunks skip tick/rise/sink.
- Enabling sails or engines with no matching blocks is a silent no-op. Inspect shows no
  actuator force.
- Runtime move failure restores the previous pose.
- Invalid config (non-finite densities, overlapping sail/envelope keys, negative thrust)
  fails plugin enable. Overlap of sail and envelope keys is a load error so wool cannot be
  both cloth and gasbag.

## Testing

Prove through `Physics.step` (and factory construction), not `apply` alone:

- A hull with extra stacked deck blocks has larger mass than the same hull without them.
- Engine blocks add mass with `enginesEnabled = false`; no `MediumThrustForce` on the body.
- Cloth adds mass with `sailsEnabled = false`; no `PressureSailForce`.
- Envelope cells in empty air produce net-up. Non-envelope cells do not add aerostatic volume.
- A hull with no envelope cells does not hover in empty air.
- Hull in liquid still lifts with no envelope cells.
- One vehicle may have waterline lift and envelope lift attached together.
- `archimedes.json` round-trips without sail/engine flags; load defaults both to on.
- `/arch sails` and `/arch engines` flip the flags on the targeted vehicle.

## Migration

Mechanical type rename of the domain, not a second parallel model:

| Today | This spec |
|-------|-----------|
| `Ship` | `Vehicle` |
| `ShipBlock`, `ShipOrigin`, `ShipPose`, `ShipTransform` | `VehicleBlock`, `VehicleOrigin`, `VehiclePose`, `VehicleTransform` |
| `ShipService`, `ShipRuntime`, `ShipPhysics`, `ShipStore` | `VehicleService`, `VehicleRuntime`, `VehiclePhysics`, `VehicleStore` |
| `ShipBody`, `ShipMassModel`, `ShipSails` | `VehicleBody` / factory, `VehicleMassModel`, `VehicleSails` |

Keep a single type. Do not leave `Ship` as a wrapper. Tests and living specs update in the
same work. Player strings may still say “ship” where that is current copy (`Cannot assemble:`);
do not mass-rewrite flavor text in this spec.

## Decisions

| Decision | Why |
|----------|-----|
| `Vehicle` is the only domain type | Physics applies to the aggregate, not to parts, and not to a `Ship` vs `Airship` pair |
| Factory rebuilds `Body` each tick | Force lists are immutable; flags change composition without mutating a live body |
| Buoyancy is intrinsic | Medium plus parts; no kind inference |
| All captured blocks add mass | Stacked deck cargo, turbines, and furled cloth still weigh |
| Turbines = engines = `MediumThrustForce` | Same density-scaled thrust; block and medium differ |
| `/arch` stays the command | Product is Archimedes; this spec is not a command rename |
| Actuator flags are not persisted | Restart defaults them on; schema stays compatible |
| Envelope materials disjoint from cloth | Wool is sails, not a hidden gasbag |
| Envelope lift sums envelope cells only | A single gasbag must not treat the oak hull as displaced air |

## Follow-up after approval

Promote these decisions into `docs/specs/ship-model.md` (vehicle model), `physics.md`,
`commands.md`, and `buoyancy.md`. Implementation is a separate plan.
