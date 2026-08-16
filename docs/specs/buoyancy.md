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

- Physics is unit-testable without Bukkit: `BuoyancySurface` interface keeps engine/resolver pure; `BukkitBuoyancySurface` adapts `World` (`isWater` = exact `Material.WATER`; `isClear` = air or water).
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
- [x] Pose persisted in `ships.json`; legacy load `y=0`
- [x] Disassembly restores at `origin + anchorDy`
- [x] Blocked-path rejection: tick resets velocity; rise/sink reject without clearing velocity (checked individually — see Next); runtime failure restores pose only
- [x] Constants configurable via `ShipConfig`/loader + `ShipsPlugin` engine wiring: `physics-ticks`, `bob-amplitude`, `max-rise`, `gravity`, `water-density`, `block-density`, `damping`

### Current notes

- `sink` requires a positive block count at the command and service boundaries; the domain defensively rejects non-positive values. Successful positive sinks remain unbounded below the waterline and leave velocity untouched (test asserts `-3.0` pose).
- Current geometry-only behavior is intentional: riders contribute no load, and equilibrium is not a force-balance solve. The exact current mass expression is `weight = mass × blockDensity × gravity`.

## Next

- [ ] Aggregate per-material mass plus rider load, with equilibrium solved from displaced water. This replaces the current geometry-based waterline equilibrium; boarding should change draft.
- [ ] Verify surface-area behavior from the displacement model: `Δdraft ≈ Δmass ÷ (waterDensity × footprint)` in tests
- [ ] Decide rise/sink velocity semantics: currently only `tick` resets velocity on blocked path; `rise`/`sink` reject without clearing
- [ ] Document settling behavior: sub-0.001 move threshold stores velocity and returns false (no move, no path check)
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
| 2026-08-16 | Living specs in `docs/specs/`; dated docs stay in `docs/superpowers/` | User directive |
| 2026-08-14 | Rigid-body bobbing with real buoyancy mechanics (user choice) | Fractional pose needed for oscillation |
| 2026-08-14 | Shallowest column surface = effective waterline | Hull floats at shallowest water it sits in |
| 2026-08-14 | Manual sink requires a positive block count; successful sinks remain unbounded below waterline with velocity untouched | Domain and command validation; debug utility semantics |
## Open questions

- [ ] Material model: per-material density values vs boolean buoyant flag vs tiers — which surface in config?
- [ ] Default densities: baseline table (e.g. oak ≈ 0.6, stone ≈ 2.7); how are unknown materials treated (default density)?
- [ ] Rider mass: fixed default for players; do mobs/items count?
- [ ] Equilibrium under load: solve force balance (`submergedVolume(y) × waterDensity = totalMass`) or extend the waterline heuristic with a load offset?
- [ ] Should physics tick be interpolated for player-perceived smoothness at low TPS?
- [ ] Should equilibrium consider water *inside* the hull (columns over air pockets)?
