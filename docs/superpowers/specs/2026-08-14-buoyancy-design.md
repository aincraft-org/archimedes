# Buoyancy Design

> **Status: implemented** (2026-08-14). See `2026-08-14-buoyancy.md` (implementation plan) and the `feat:`/`fix:` commits after $dd7ad46$.

## Goal

Give assembled ships a rigid-body vertical buoyancy: a ship floats on water, rises/sinks to its equilibrium waterline, and bobs vertically with a damped oscillation driven by real buoyancy mechanics (buoyancy force vs. weight). No horizontal movement, steering, or rotation — there are no horizontal movement vectors yet.

## Platform

Extends the `Ships` plugin (Paper 26.2, Java 25, Gradle Kotlin DSL, Paperweight Userdev, `runServer`, Spotless/Checkstyle/PMD/SpotBugs gates). Package `dev.jlo.ships`. The existing `2026-08-13-ship-building-design.md` spec describes assembly, rendering, walkable decks, persistence, and reconciliation; this design extends it.

## Scope Decision

The user chose **full moving-ship buoyancy** with **rigid-body bobbing** ("gentle bobbing is like actual rigid body so you need like buoyancy mechanics") and **no horizontal movement yet** ("horizontal movement not yet cause there's no horizontal movement vectors yet"). This milestone therefore implements vertical rigid-body motion with real buoyancy physics; horizontal translation and rotation remain explicitly deferred.

## Core Model

A ship is a rigid body with a **runtime pose**: a fractional vertical position above its build-site origin. The block model stays origin-relative; the pose is what moves.

- **`ShipPose`** (new, model): immutable `{ double y }` — fractional vertical offset from the build-site origin. The physics integrator needs fractional position/velocity for real bobbing; integer-only `dy` would lose the oscillation.
- **`Ship`**: gains a mutable pose (`pose()`, `setPose(ShipPose)`). `ShipOrigin` stays immutable.
- **Collision/anchor**: the integer **collision anchor** is `floor(y)` — the whole-block position used for deck supports, collision, and disassembly restore. The fractional `y` drives smooth visual bobbing; the anchor snaps only when the ship crosses a whole-block boundary.
- **Renderer** positions block displays at `origin + y + relative` (fractional, smooth bob). **Deck supports and collision** use `origin + anchor + relative` (integer). **Disassembly restores at `origin + anchor`** (where the ship actually is), never the stale build site.
- **Anchor tracking**: when `floor(y)` changes by ±1, the integer anchor moves one block. The deck manager must then re-deploy supports at the new anchor (validating every new support cell is clear first) and remove the old anchor's supports, all before the anchor is committed. Supports never linger at a stale anchor.

## Persistence

`ships.json` gains an optional `pose` object (`{ "y": 12.5 }`). Old files without `pose` load with `y = 0` (backward compatible). The pose is persisted so a server restart reconstructs each ship at its current vertical position, not the build site.

## Buoyancy Physics

Vertical rigid-body integration on a server tick:

- **Mass** = block count (each block is one mass unit). **Weight** = `mass × gravity`.
- **Buoyancy force** = `waterDensity × gravity × submergedVolume`.
- **Submerged volume** = number of ship blocks whose waterline depth is at or below the current water surface. Waterline is resolved **per column**: for each ship column, the highest water block under the hull defines that column's surface; the ship's effective surface is the minimum over columns (the hull floats at the shallowest water it sits in).
- **Net force** = buoyancy − weight → vertical acceleration → velocity → `y`. Integrated each tick with a fixed timestep using fractional `y` and `velocity` (double).
- **Damping** applied to velocity each tick (e.g. `velocity × damping`) so the ship settles to equilibrium rather than oscillating forever.
- **Equilibrium** is the `y` where buoyancy ≈ weight; the ship naturally rises if too deep and sinks if too high.

The engine never teleports the ship to a computed target; it integrates forces and applies the resulting small `y` change, validating the integer collision path is clear first.

**Vertical bounds (sinking contract):** the ship's vertical motion is bounded at the waterline. Buoyancy drives the ship up to its equilibrium at the surface and it bobs around that waterline; it never sinks below its build-site waterline (the hull rests at the surface). This resolves the apparent contradiction with "real buoyancy mechanics": buoyancy force and weight still determine the equilibrium and the bobbing amplitude, but the ship is not allowed to sink below the surface it was built at. Sinking below the waterline is explicitly deferred.

## Components

### `buoyancy` package (mirrors `deck` structure)

- **`BuoyancySurface`** (interface): `isWater(x,y,z)`, `isAir(x,y,z)`, `setBlock(x,y,z,data)` — separated so physics rules are unit-testable without Bukkit.
- **`BuoyancyResolver`**: computes per-column waterline and equilibrium `y` from the ship model.
- **`BuoyancyEngine`**: per-tick integration (mass, submerged volume, buoyancy, gravity, damping) producing a new fractional `y`; validates every intermediate integer cell is clear (air or water) before applying; repositions renderer and deck supports.
- **`BuoyancyImpl`**: `rise(ship)` (initial float-up on assembly), `tick(ship)` (bob), `sink(ship)` (manual). All-or-nothing: any blocked cell aborts the move with the ship unchanged.

### Service changes (`ShipServiceImpl`)

- `assembleAt` → after render, `buoyancy.rise(ship)`; on failure, full rollback to the build site (existing pattern).
- `disassemble` → restore at `origin + anchor`, then clear the pose.
- New `tick()` on the service, registered as a Bukkit scheduler task, drives `buoyancy.tick` for each ship.

### Command changes (`ShipCommand`)

- `/ship buoyancy` — toggle buoyancy on/off for the targeted ship.
- `/ship sink` — manually lower the ship by a configurable amount (debug/utility).

### Config (`config.yml`)

- `buoyancy-enabled` (default true)
- `physics-ticks` (integration tick interval)
- `bob-amplitude` (max vertical oscillation)
- `max-rise` (maximum vertical rise from build site)
- `gravity`, `water-density`, `damping` (physics constants)

## Safety Invariants

1. **Restore follows pose** — disassembly restores where the ship visually is, never the stale build site.
2. **Path validation before any mutation** — every intermediate integer cell must be clear (air/water) before the ship moves.
3. **Rollback on failure** — if buoyancy fails after render, full rollback to the build site.
4. **Pose persisted** — restart reconstructs the ship at its current `y`.
5. **Backward compatible persistence** — old `ships.json` files load with `y = 0`.

## Explicitly Deferred

- Horizontal movement, steering, acceleration, rotation (no horizontal vectors yet)
- Collision with other ships or terrain during movement
- Seats, passengers, players standing on a moving ship
- Sinking below the waterline, drowning, or water entry effects (vertical motion is bounded at the waterline)
- Real mass/density per material (uniform block mass for now)

## Verification

Unit tests cover: pose persistence round-trip (with and without `pose` field), waterline resolution per column, submerged-volume computation, equilibrium `y` computation, force integration over ticks (rise/settle/bob), fractional-position bobbing, path-clear validation before moves, and disassembly restoring at `origin + anchor`.

Runtime verification uses `./gradlew runServer`: build a hull in water, assemble it, confirm it rises to the waterline and bobs, restart the server and confirm it reconstructs at the floated position, then disassemble and confirm blocks restore at the floated position. The build must pass the full `check` lifecycle.
