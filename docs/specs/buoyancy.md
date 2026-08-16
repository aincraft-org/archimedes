# Buoyancy — Living Spec

> Status: active
> Last updated: 2026-08-16
> Owners: jlo

Give assembled ships rigid-body vertical buoyancy with a geometry-based waterline equilibrium and damped bobbing. No horizontal movement, steering, or rotation.

A ship is one rigid body: a single pose moves the whole hull. Current buoyancy uses uniform block density for integration and no rider mass; the waterline/equilibrium behavior is geometry-based rather than a force-balance solve. Aggregate per-material mass and rider load are intended under Next.

Success looks like: a ship floats at the shallowest water it sits in, bobs gently around its geometry-based equilibrium, and restores/disassembles at its actual floated position. Load-sensitive draft and force-balance equilibrium are Next behavior.

## Boundaries

### In scope

- `Buoyancy` / `BuoyancyImpl` — rise/tick/sink/clear lifecycle
- `BuoyancyEngine` — fixed-timestep vertical integration
- `BuoyancyResolver` — per-block waterline sampling, submerged volume, equilibrium
- `BuoyancySurface` / `BukkitBuoyancySurface` — water/air/world access
- Pose persistence and anchor-based restoration coupling

### Out of scope / non-goals

- Horizontal movement, steering, acceleration, rotation
- Collision with terrain: blocked path cells reject movement (`pathClear` fails on non-air/non-water). No ship-vs-ship or dynamic collision modeling.
- Passenger transport is separate (entity carry teleports riders; see `ship-runtime`) — but rider *mass* as dynamic load is planned (see Next)
- Sinking below waterline by physics (manual `/ship sink` may go negative — current behavior)

## Invariants

- Current buoyancy uses geometry-based waterline equilibrium, uniform block density, and no rider mass.
- Integration per tick: `a = (buoyancy − weight)/mass`; `v' = (v + a·dt) · damping`; `y' = y + v'·dt`. Ship never teleports; it integrates.
- Waterline resolution is **per block**: each block samples its own column window (`bottom+64` down to `bottom−64`, where `bottom = origin.y + anchorDy + rel.y`); the effective surface is the minimum (shallowest) surface over all sampled columns. A solid (non-clear) block seals a column (no water below it in the window). Not a per-column algorithm — the window shifts with each block's own y.
- Submerged volume counts each block whose `blockY <= its sampled column surface` (surface recomputed per block at that block's own y).
- Column scan window: `bottom+64` down to `bottom−64`; a solid (non-clear) block seals the column (no water below it).
- `equilibriumY = surfaceY − origin.y − minRelativeBlockY` (`surfaceY` already embeds `pose.anchorDy()` via sampling); `0` when no water. Current equilibrium is the sampled geometry waterline, not a force-balance solve.
- `rise` targets `min(maxRise, equilibriumY)`; the whole integer path (air or water) is validated before any move; blocked → `false`, no change.
- `tick` clamps `y` to `[equilibrium − bobAmplitude, min(maxRise, equilibrium + bobAmplitude)]`, reflects velocity at bounds; move threshold `<0.001`; blocked path → reject and reset velocity.
- Pose + runtime move together: set pose, `runtime.move`, and on `ShipRuntimeException` restore only the pose (`moveTo`). Runtime may have partially moved renderer/collisions before failing — the move itself carries rollback guarantees; buoyancy does not re-rollback.
- Disassembly restores at `origin.y + anchorDy()` — the floated position, never the stale build site.
- Disabled buoyancy: `rise` → true (no-op), `tick` → false, `sink` → false. `toggleBuoyancy` persists the flag per ship.

## Implementation guidance

- Physics is unit-testable without Bukkit: `Buoyancy` / `BuoyancySurface` live in `:api`; engine/resolver/`BuoyancyImpl` live in `:common`; `BukkitBuoyancySurface` lives in `:paper` and adapts `World` (`isWater` = exact `Material.WATER`; `isClear` = air or water).
- Per-ship state (velocity, equilibrium) lives in `BuoyancyImpl` maps keyed by ship UUID; cleared on disassembly/rollback (`clear(Ship)`).
- `ShipService.tick` drives `buoyancy.tick` per ship and persists once iff any ship moved; the scheduler only runs when the **global** `config.buoyancy-enabled` is true (per-ship toggles cannot re-enable physics when the scheduler is off).
- Path clearance: `BuoyancyImpl.pathClear` hand-rolls integer cells from `floor(min/max y)` spans over `origin + y + rel` (no `ShipTransform.cell` call today); allowed when air or water. Prefer centralizing this with the transform in future.
- Tests: waterline sampling, per-block submerged counts, equilibrium, rise/settle/bob integration, clamps, blocked-path rejection, negative-pose sink, pose persistence round trip (with and without `pose`), restore-at-anchor.
- Quality gate: `./gradlew check` (Java 25, Paper 26.2, Spotless/Checkstyle/PMD/SpotBugs).

## Current

- [x] Rise to equilibrium with all-or-nothing path validation
- [x] Per-tick damped oscillation with clamp + velocity reflection
- [x] Per-block waterline sampling, shallowest-surface rule, submerged-volume counting
- [x] Manual `/ship sink N` (path-checked; may pass below waterline)
- [x] Buoyancy toggle per ship (`/ship buoyancy`)
- [x] Pose persisted in `archimedes.json`; leftover `ships.json` still loads; missing pose → `y=0`
- [x] Disassembly restores at `origin + anchorDy`
- [x] Blocked-path rejection: tick resets velocity; rise/sink reject without clearing velocity (checked individually — see Next); runtime failure restores pose only
- [x] Constants configurable via `ShipConfig`/loader + `ArchimedesPlugin` engine wiring: `physics-ticks`, `bob-amplitude`, `max-rise`, `gravity`, `water-density`, `block-density`, `damping`

### Current notes

- Positive manual sink remains unbounded below the waterline and does not alter velocity. The command and service require `blocks >= 1`; `BuoyancyImpl` also rejects non-positive values defensively.
- Current geometry-only behavior is intentional: riders contribute no load, and equilibrium is not a force-balance solve. The exact current mass expression is `weight = mass × blockDensity × gravity`.

## Next

- [ ] Implement the approved force-balance mass model in `docs/superpowers/specs/2026-08-16-buoyancy-mass-model-design.md`: namespaced per-material densities with validated positive finite fallback, tracked-player-only runtime load, bounded deterministic interpolation, immutable diagnostics, and explicit no-equilibrium states
- [ ] Verify footprint behavior: `Δdraft ≈ Δmass ÷ (waterDensity × footprint)` within the approved discrete tolerance
- [x] Keep positive manual sink unbounded below the waterline with velocity untouched; command, service, and domain boundaries reject non-positive distances
- [x] Decide rise/sink velocity semantics: currently only `tick` resets velocity on blocked path; `rise`/`sink` reject without clearing
- [x] Approve material/rider equilibrium contract: config-only densities, runtime-only tracked-player mass, no `archimedes.json` schema change, separate validated `max-fall` default `16.0`, and deterministic overloaded descent/clamp
- [x] Document settling behavior: sub-0.001 move threshold stores velocity and returns false (no move, no path check)
- [ ] Record live acceptance: assemble hull in water, observe rise/bob, restart reconstructs at floated position, disassemble restores at floated position

## Future

- [ ] Horizontal buoyancy-coupled movement (planing, drag)
- [ ] Propulsion physics: thrust force model (engines/propellers), water drag/resistance, steering/yaw dynamics, fuel — 2D/3D state (position, velocity, heading, angular velocity) integrated with the vertical integrator
- [ ] Water entry/splash effects; drowning rules
- [ ] Multi-ship water displacement (no shared water physics today)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | Per-block buoyancy + dynamic rider load committed as design direction; mechanism (density vs flag, equilibrium solve) open | User directive: some blocks must be buoyant; player load should dip a raft |
| 2026-08-16 | Approved per-material density + tracked-player load contract; no persistence schema change | Deterministic force balance and runtime-only load avoid stale persisted mass |
| 2026-08-16 | Overloaded ships integrate downward to a fixed `max-fall` bound, then clamp with zero downward velocity | Prevent fabricated equilibrium and ratcheting descent while preserving normal path validation |
| 2026-08-16 | Living specs in `docs/specs/`; dated docs stay in `docs/superpowers/` | User directive |
| 2026-08-14 | Rigid-body bobbing with real buoyancy mechanics (user choice) | Fractional pose needed for oscillation |
| 2026-08-14 | Shallowest column surface = effective waterline | Hull floats at shallowest water it sits in |
| 2026-08-16 | Positive manual sink remains unbounded below waterline; non-positive input rejected at command, service, and domain boundaries | Preserve debug utility without allowing negative input to raise a ship |

## Open questions

- [x] Material model: validated namespaced per-material density table with positive finite default fallback
- [x] Rider scope: tracked players only, fixed positive finite configured mass, runtime-only
- [x] Equilibrium: bounded deterministic force-balance interpolation with immutable diagnostic result
- [x] Persistence: configuration-only density and runtime-only rider state; no `archimedes.json` schema change
- [ ] Should equilibrium consider water *inside* the hull (columns over air pockets)? This is outside the approved first implementation.
