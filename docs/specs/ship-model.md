# Ship Model — Living Spec

> Status: active
> Last updated: 2026-08-16
> Owners: jlo

## Intent

The Ships plugin turns ordinary block builds into persistent, stationary ships. The model domain owns the *data*: what a ship is, how its blocks are captured and projected, how it is persisted, and how it is configured. Rendering, collision, buoyancy, and commands are separate domains that consume this data.

Success looks like: a `Ship` is a pure, unit-testable description of a build (origin + relative block snapshots + pose), `ships.json` round-trips it exactly, and every other domain derives positions from one canonical transform.

## Boundaries

### In scope

- `Ship`, `ShipOrigin`, `ShipBlock`, `BlockPos`, `ShipPose` model classes
- `ShipTransform` — the canonical coordinate projection (visual / authoritative cell / collision anchor)
- `ShipScanner` — bounded six-directional component capture
- `ShipStore` — `ships.json` persistence, atomic writes, backward compat
- `ShipConfig` / `ShipConfigLoader` — config.yml surface and validation
- Forbidden-material policy, `disabled-worlds`, size bounds

### Out of scope / non-goals

- Entity rendering, collision volumes, buoyancy physics, entity carry (own specs)
- Command parsing and permissions (see `commands`)
- Horizontal movement, rotation, propulsion, seats, damage, docking

## Invariants

- `ShipBlock` snapshots pair a relative position with the exact `BlockData` string captured from the world at assembly (`ShipServiceImpl` via `WorldMutator.blockDataAt`); restoration must reproduce the original block. The scanner itself captures positions only.
- `Ship.blocks` is immutable after construction; `pose` and `buoyancyEnabled` are the only mutable state.
- `ShipPose.anchorDy() = floor(y)`; the integer anchor drives collision, clearance, restoration, and persistence semantics. Fractional `y` drives visuals only.
- `ShipTransform` is the canonical projection for rendering and hulls (visual / cell / collision anchor). World-boundary code may derive integer cells from `origin + floor(pose)` (e.g. `BukkitWorldMutator.baseY`, `BuoyancyImpl.pathClear`) but must never duplicate the visual/anchor arithmetic or add offsets.
- `ships.json` is the single persistence authority; entities are never persisted (non-persistent at Bukkit level).
- Persistence must stay backward compatible: missing `pose` → `y=0`; missing `buoyancy` → enabled.
- Saves are atomic: write temp file, then replace; an interrupted save never leaves a truncated primary file.
- Assembly policy is checked before scanning: a target outside the primary bound world is rejected with `Ship assembly is not permitted in this world`; a disabled bound world is rejected with `Ship assembly is disabled in this world`. Neither path scans or mutates blocks.
- Forbidden materials are lowercased at load; blank entries dropped; invalid UUIDs in `disabled-worlds` fail load (plugin disables rather than misbehaving).
- Unsafe supplied config values never silently fall back — validation rejects them and plugin enable fails with a clear log. Missing list and optional scalar keys fall back to code defaults; `maximum-blocks` and `target-distance` instead use loader default `0` and fail validation when absent.
## Implementation guidance

- Model classes live in `dev.jlo.ships.model`; records for value types (`BlockPos`), final classes with accessors elsewhere. `ShipOrigin` intentionally has no `equals` (identity semantics).
- JSON via Gson in `ShipStore`; version field not present — compatibility is handled by optional fields only.
- `StoreAdapter` (in `ShipsPlugin`) wraps store I/O checked exceptions into service-contract exceptions; `ShipStore.loadAll()` and `saveAll()` declare `IOException` for filesystem failures, while malformed JSON parsing errors such as Gson's `JsonSyntaxException` propagate unchecked from `loadAll()`.
- Config loading pattern: `ShipConfigLoader` validates every supplied value. Missing `maximum-blocks` and `target-distance` are read with a numeric default of `0`, then fail the positive-value checks; missing list keys produce empty forbidden-material and disabled-world sets; other missing keys use the code defaults: buoyancy enabled, physics ticks `1`, bob amplitude `0.5`, max rise `16.0`, gravity `0.05`, water density `1.0`, block density `0.5`, and damping `0.9`. Unsafe supplied values fail enable; missing keys do not generally fall back for the two required positive integers.
- Tests mirror packages: `model/`, `scan/`, `store/`, `config/` JUnit suites must stay green under `./gradlew check` (Spotless + Checkstyle + PMD + SpotBugs, Java 25, Paper 26.2).

## Current

- [x] Six-directional bounded BFS scan: aborts (incomplete result) on size-limit exceed or forbidden seed/current block; forbidden neighbors are skipped as boundaries
- [x] Per-block snapshots: scanner yields relative positions; `ShipServiceImpl` snapshots exact `BlockData` strings into immutable `ShipBlock`s
- [x] Canonical transform: `visual` (corner), `cell` (floor anchor), `collisionAnchor` (visual X/Z +0.5, Y unchanged), `visual(ship, rel, y)` overload for old-pose queries
- [x] `ships.json` schema: root array; per ship `id`, `owner`, `origin{world,x,y,z}`, `blocks[{pos{x,y,z}, data}]`, optional `pose{y}`, optional `buoyancy:false`
- [x] Atomic save (tmp + ATOMIC_MOVE with fallback)
- [x] Legacy file load (`y=0`, buoyancy enabled)
- [x] Config: `maximum-blocks`, `target-distance`, `forbidden-materials`, `disabled-worlds`, buoyancy/physics constants; strict validation of supplied values, code defaults for missing keys

### Current notes

- `ShipScanner` returns `ScanResult` with a single defensive copy of captured positions; the accessor returns that immutable stored list.
- `config.yml` + loader defaults: max 2048 blocks, target distance 8, `buoyancy-enabled true`, `physics-ticks 1`, `bob-amplitude 0.5`, `max-rise 16`, `block-density 0.5`, `water-density 1.0`, `gravity 0.05`, `damping 0.9`.

## Next

- [x] Scan result defensively copied; `captured()` exposes the immutable stored list

## Future

- [ ] Cross-world ship support: world UUIDs are already persisted (`ShipStore`, `WorldMutator` keyed by world); current runtime binds assembly to the primary Bukkit world (`ShipsPlugin.WorldBinding` → `Bukkit.getWorlds().get(0)`). Command resolution may identify another world, but assembly is rejected there. Once Task 3 lands, `disabled-worlds` must also reject the bound world.
- [ ] Implement approved aggregate per-material mass and tracked-player runtime load for buoyancy equilibrium; densities remain configuration-only and `ships.json` gains no mass fields (see `docs/superpowers/specs/2026-08-16-buoyancy-mass-model-design.md`)
- [ ] Versioned persistence schema with migration path

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | Living specs live in `docs/specs/`; dated docs in `docs/superpowers/` remain historical record | User directive; one maintained catalog per domain |
| 2026-08-16 | Material densities are configuration-only; rider mass and equilibrium diagnostics are runtime-only | Avoid schema migration and stale persisted load; recompute from blocks, config, and tracked players |
| 2026-08-14 | Pose persisted as optional field, not schema version bump | Backward compat without migration machinery |
| 2026-08-14 | `anchorDy = floor(y)` is authoritative for collision/restoration | Integer cells must match world semantics; fractional y is visual only |

## Open questions

- [ ] Should `ShipOrigin` gain `equals`/`hashCode` (currently identity-only)?
- [ ] Does `ships.json` need a schema version once optional fields multiply?
