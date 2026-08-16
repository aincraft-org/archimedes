# Buoyancy Mass Model Design

> **Status: Approved on 2026-08-16.** This document is the accepted design contract. Implementation remains separate and all implementation boxes remain unchecked.
> **Execution date:** 2026-08-16

## Decision requested

Approve or reject the complete mass, rider-load, equilibrium, configuration, and persistence contract below. A yes authorizes a separate implementation plan; it does not itself authorize implementation or changes to `docs/specs/`.

## Scope and vocabulary

This design extends the existing rigid-body vertical buoyancy model. A ship retains one pose; `ShipPose.anchorDy() = floor(y)` remains authoritative for integer collision, clearance, restoration, and persistence semantics, while fractional `y` remains visual and physical. Existing per-block waterline sampling supplies `displacedVolume(y)` and the footprint/surface measurements. The design replaces the current geometry-only equilibrium with force balance while retaining fixed-timestep integration, damped bobbing, all-or-nothing path validation, and runtime move rollback behavior.

Definitions:

- `blockMass`: aggregate mass of captured ship blocks, in mass units.
- `riderMass`: runtime mass of tracked players currently associated with the ship.
- `totalMass = blockMass + riderMass`.
- `waterDensity`: existing positive global water-density setting, in mass units per block volume.
- `displacedVolume(y)`: geometry-derived submerged volume at pose height `y`, using the current per-block column sampling rules.
- `equilibriumY`: a pose height at which displaced water mass equals total mass, if one exists.
- `draft`: vertical distance from the water surface reference to the ship's effective footprint/lower reference at equilibrium. The footprint acceptance uses the actual effective footprint, not block count.

### Explicit non-goals in this draft

- No horizontal motion, steering, rotation, propulsion, water entry effects, shared multi-ship water physics, or ship damage.
- No mobs, items, armor stands, projectiles, or arbitrary passengers in the load model.
- No ship-specific mass overrides in `ships.json`.
- No persistence of rider membership, rider mass, equilibrium cache, or density snapshots.
- No schema version field or migration for `ships.json`.
- No interpolation requirement for the physics tick; the solver is deterministic and may use a discrete sample grid.

## 1. Material density configuration

### 1.1 Syntax and namespace

Add a namespaced mapping under the buoyancy configuration, separate from legacy scalar constants:

```yaml
buoyancy:
  material-densities:
    minecraft:oak_planks: 0.60
    minecraft:stone: 2.70
  default-material-density: 1.00
```

The exact YAML nesting may follow the existing `ShipConfig` loader style, but the semantic namespace is fixed: keys are namespaced material identifiers (`namespace:path`) and values are densities in the same mass-per-block-volume units as `water-density`. The default is explicitly positive and finite.

The implementation must not infer density from display names, localized names, tags, block hardness, or Bukkit enum ordering. A material's canonical key is its namespaced Minecraft key, lowercased using locale-independent rules.

### 1.2 Normalization

At load:

1. Read mapping keys as strings and trim surrounding ASCII/Unicode whitespace accepted by the YAML parser.
2. Lowercase namespace and path with locale-independent lowercasing.
3. Require the normalized key to be a valid namespaced material key (`namespace:path`) and resolve it against the server material registry when registry resolution is available.
4. Normalize values to a numeric density; reject booleans, nulls, strings that are not exact finite numeric values, `NaN`, positive/negative infinity, zero, and negatives.
5. Normalize the default by the same finite-positive rule.

Runtime block lookup uses the same canonical namespaced key. Configuration lookup is therefore stable across case and harmless surrounding whitespace differences.

### 1.3 Duplicate, missing, and invalid behavior

- **Duplicate after normalization:** fail plugin enable with a clear configuration error naming the colliding normalized key. Never choose first-wins or last-wins; silent order dependence is unsafe.
- **Missing material key:** use `default-material-density`, including for a valid material absent from the table and for a material unknown to the table. Unknown runtime materials do not disable the ship or throw during a physics tick.
- **Missing mapping:** treat it as an empty table and use the validated default for all materials.
- **Missing default:** use the code default `1.0`, then validate it. This is a deliberate compatibility fallback for an absent optional setting.
- **Unknown configuration material key:** fail enable rather than silently accepting a typo. This is distinct from an unknown runtime material, which uses the default.
- **Invalid supplied density, default, key, or mapping shape:** fail enable before runtime registration, with the offending path/key in the message. Do not silently fall back from an explicitly supplied unsafe value.

### 1.4 Aggregate block mass

For each captured `ShipBlock`, resolve its material from the captured `BlockData` and sum its configured density:

\[
blockMass = \sum_{b \in blocks} density(material(b)).
\]

The aggregate is deterministic for an immutable `Ship.blocks` list. Equal-volume blocks with different configured densities therefore have different masses even when their geometry is identical.

### Alternatives considered

- A boolean `buoyant: true/false` flag loses material gradation and makes mixed-material aggregates arbitrary.
- Hard-coded tiers avoid config complexity but cannot represent server packs or deliberate balancing.
- Deriving density from hardness is unstable and unrelated to intended buoyancy.
- Rejecting unknown runtime materials would make persisted ships fragile across registry/plugin changes. Conservative behavior is a validated positive fallback at runtime, while configuration typos still fail enable.

## 2. Tracked-player rider load

### 2.1 Scope and units

Only players present in the existing ship rider tracker count. Mobs, items, dropped items, non-player passengers, and merely nearby entities count as zero. Each tracked player contributes a fixed configured `player-mass` in the same mass units as block density; default `player-mass` is `1.0`, and it must be positive and finite.

\[
riderMass = playerCount \times playerMass.
\]

Rider mass is runtime-only. It is not a property of `Ship`, is not serialized, and is not inferred from Bukkit entity attributes or inventory.

### 2.2 Timing and lifecycle

The tracker is the sole authority for membership. The effective count is refreshed at deterministic physics boundaries:

- On successful boarding/tracker add, the next buoyancy tick observes the new player; an immediate recalculation is optional but must not produce a second conflicting move.
- On successful unboarding/tracker removal, the next buoyancy tick observes the reduced count.
- At the start of each physics tick, snapshot tracked players, discard invalid/offline/dead/non-player entries, and compute a count in stable UUID order. The order does not affect the sum but makes diagnostics deterministic.
- If a tracker event is missed, the next tick's tracker snapshot is authoritative; no persisted rider state is restored.
- Rider changes affect the target equilibrium used by subsequent integration, not historical pose or a teleport to an ideal height.

### 2.3 Best-effort carry semantics

Existing vertical carry remains best effort. A carry teleport failure, unavailable entity, cross-world state, or stale tracker entry does not fail buoyancy, invalidate the ship, or roll back the ship pose. The player remains counted only while the tracker still reports a valid tracked player; stale entries are removed during the next snapshot. A ship move failure still follows the existing runtime transaction contract: restore the old pose, preserve the runtime's rollback guarantees, and keep the original `ShipRuntimeException` context.

### Alternatives considered

- Counting all entities is noisy and allows dropped items or hostile mobs to change draft unexpectedly.
- Reading player physical weight/attributes makes the model server-dependent and non-deterministic.
- Recalculating on every event risks event-order races; next-tick snapshots preserve deterministic physics boundaries while still making boarding visible promptly.

## 3. Equilibrium equation and solver

### 3.1 Force balance

At each candidate pose height `y`, compute the submerged geometry using the existing per-block waterline/column rules. The equilibrium equation is:

\[
displacedVolume(y) \times waterDensity = blockMass + riderMass.
\]

The left side is displaced water mass; the right side is total load. Gravity cancels because it multiplies both sides. `waterDensity` is validated positive and finite at configuration load.

### 3.2 Candidate bounds

The solver operates over a finite deterministic interval:

- `lowerBound = currentY - maxFall`;
- `upperBound = currentY + maxRise`;
- `maxFall` is a separate positive finite configuration value, default `16.0` blocks. It bounds solver search and automatic overloaded descent; it does not limit explicit manual `/ship sink`. `maxRise` continues to bound upward solver search/movement.

The solver samples integer anchor heights and, where a sign change occurs between adjacent integer samples, linearly interpolates within that one-cell interval. Let `F(y) = displacedVolume(y) × waterDensity - totalMass`. A candidate is accepted when `|F(y)| <= massTolerance` or when the interval width reaches `draftTolerance`; the interpolated result is then clamped to the bracket. Defaults: `massTolerance = 1e-6` mass units and `draftTolerance = 1e-3` blocks, both positive finite configuration values. If the implementation elects a purely discrete solver, it must choose the lowest sample satisfying the tolerance and document the resulting quantization; this draft recommends bounded linear interpolation for smoother load changes.

The scan is deterministic: evaluate heights from lower to upper in ascending order, use the first valid crossing/bracket, and never depend on hash-map iteration order. The shallowest effective water surface remains the waterline reference for each geometry sample.

### 3.3 Monotonicity assumption and guard

Over a fixed water/world snapshot and rigid vertical translation, `displacedVolume(y)` is assumed non-increasing as `y` rises (raising a ship cannot increase its submerged volume). The solver must verify sampled monotonicity within a small numerical epsilon. If a sampled increase larger than epsilon occurs because of discontinuous terrain/water columns, treat the interval as non-monotonic and select the first valid tolerance candidate in scan order rather than extrapolating across it. Never run an unbounded iterative method on a non-monotonic function.

World changes between samples are handled as a changed surface snapshot: abort the solve and use the explicit no-solution/hold behavior below rather than mixing samples from incompatible snapshots.

### 3.4 No-solution behavior

- **Always sinking / overloaded:** if `F(y) < -massTolerance` throughout the bounded interval, return `NO_EQUILIBRIUM_SINKING`. Continue normal damped integration downward, with path validation, until `lowerBound`; clamp at `lowerBound` and set negative velocity to zero. Recompute the bounded interval only when mass, configuration, or water geometry changes, not from every descended pose, so the bound cannot ratchet downward. Never teleport to a fabricated target, disable the ship, or delete it.
- **Too light / above all sampled water:** if `F(y) > massTolerance` throughout the interval, return `NO_EQUILIBRIUM_ABOVE_WATER` and hold at the current pose. Do not rise without a valid bracket.
- **No water / sealed columns:** return `NO_EQUILIBRIUM_NO_WATER` and hold the current pose.
- **Non-monotonic or unstable snapshot:** return `NO_EQUILIBRIUM_UNSTABLE_SAMPLE`, hold pose for that tick, retain velocity unchanged, and retry on the next stable tick.

The solver returns an immutable diagnostic result containing status, lower/upper bounds, total mass, minimum/maximum sampled displacement, and optional equilibrium. This result is available through the service inspect/diagnostic boundary and tests, is runtime-only, and is never persisted or exposed as a mutable map.

### 3.5 Integration and state transition

The equilibrium target feeds the existing damped vertical integration:

\[
a = (buoyancy - weight) / mass,\quad v'=(v+a\,dt)\times damping,\quad y'=y+v'\,dt.
\]

The implementation must define `mass` as `totalMass` for the force calculation, with a positive finite guard. If `totalMass` is invalid at runtime (which should be impossible after config validation), hold the pose and emit a diagnostic rather than divide by zero. Existing fractional pose, move threshold `<0.001`, path checking, blocked movement, and runtime failure restoration remain unchanged unless an approved implementation plan explicitly revises them.

### 3.6 Footprint/draft acceptance equation

For a small added load `Δmass` on a stable, horizontal water surface with effective footprint area `A` and water density `ρ`, the draft change must satisfy:

\[
Δdraft \approx \frac{Δmass}{ρ A}.
\]

The automated acceptance uses a broad numerical tolerance that accounts for block discretization and interpolation (recommended relative error ≤ 10% for a sufficiently broad footprint, plus ≤ one block of absolute discretization error for narrow footprints). The test must report `A`, `ρ`, `Δmass`, measured draft delta, and both error terms. This is a validation relationship, not a second solver equation; irregular footprints and stepped water surfaces are expected to deviate within documented discrete bounds.

### Alternatives considered

- A fixed load offset added to the old waterline heuristic cannot honor mixed material mass or footprint area.
- A fully continuous fluid simulation is outside the plugin's rigid-body scope and would be non-deterministic under block water.
- A pure integer solver is simpler but causes avoidable one-block draft jumps; bounded interpolation preserves deterministic behavior and reduces visible discontinuity.

## 4. State, reload, and persistence

### Runtime state

Per-ship runtime state contains velocity, last equilibrium/no-solution status, and the current runtime rider snapshot/count. It is keyed by ship UUID and cleared on disassembly, rollback, ship removal, and plugin disable using existing lifecycle cleanup. Rider UUID membership is not persisted.

### Configuration reload

Densities, default density, player mass, water density, tolerances, and solver bounds are configuration-only. On a successful reload, newly computed block mass and subsequent rider load use the new immutable config snapshot. Every affected ship must recalculate equilibrium at the next physics tick; it must not retain a stale equilibrium target. If reload validation fails, retain the prior valid config and continue running according to the existing reload contract; never install a partial table.

A config reload does not rewrite `ships.json` merely because densities changed. It may cause the normal `ShipService.tick` persistence when a ship actually moves, preserving the existing “save once iff any ship moved” rule.

### `ships.json`

No schema change. Continue persisting ship identity, origin, captured blocks, optional pose, and buoyancy flag only. On restart, load the persisted floated pose, recompute block mass from current configuration and current captured block data, initialize rider mass to zero, and solve from the restored pose/world state. Do not persist equilibrium, draft, rider IDs, density values, or solver diagnostics. If the restored pose has no equilibrium, apply the explicit no-solution state without changing the stored pose until normal runtime behavior moves it safely.

Disassembly continues to restore blocks at `origin + anchorDy()` for the authoritative floated pose; no mass-model path may substitute the stale build site. Disassembly at an authoritative anchor remains valid regardless of fractional visual pose.

### Alternatives considered

- Persisting density snapshots would make ships immune to balancing changes but creates migration/version and stale-config complexity.
- Persisting rider load would resurrect absent entities and violate runtime-only semantics.
- Adding ship-specific overrides would require a `ships.json` schema change without a current requirement; conservative configuration-only densities avoid that compatibility cost.

## 5. Behavioral acceptance matrix

All automated cases must be deterministic, unit-testable without Bukkit where possible, and must assert observable outputs rather than implementation details. All implementation checkboxes remain intentionally unchecked pending approval.

| ID | Case | Setup / stimulus | Expected acceptance |
|---|---|---|---|
| A1 | Equal volume, different materials | Two equal-geometry ships; configure material densities `0.5` and `2.0`; same water | Heavy-material ship has greater `blockMass` and a deeper equilibrium draft; no geometry-only equality assertion remains |
| A2 | Mixed-material aggregate | One ship with known counts of two materials | `blockMass` equals exact sum of each configured density; solver uses that sum in the balance equation |
| A3 | Unknown-material fallback | Captured/runtime block key absent from table; valid positive default configured | Block uses default density; no tick exception or enable failure caused by runtime unknown key |
| A4 | Config normalization | Mixed-case and padded keys that normalize to one key | Lookup is canonical and stable; normalized duplicates fail enable with key context |
| A5 | Missing/invalid configuration | Omit mapping/default, then supply zero, negative, NaN/infinity, malformed key, invalid mapping shape | Omitted optional values use documented defaults; every explicitly invalid value fails enable before registration |
| A6 | Tracked player scope | Board player; add nearby mob/item and non-player entity | Only tracked player contributes exactly `player-mass`; other entities contribute zero |
| A7 | Boarding draft change | Stable raft, record equilibrium/draft, board one tracked player | Next physics boundary observes rider; equilibrium draft deepens; no unrelated teleport or persisted rider data |
| A8 | Unboarding draft change | Remove tracked player from same raft | Next physics boundary recomputes lower `riderMass` and shallower target; stale membership is not retained |
| A9 | Best-effort carry | Force rider carry teleport failure or stale/offline rider during movement | Ship movement remains successful if runtime move succeeds; rider failure is non-fatal and stale entry is removed at snapshot |
| A10 | Overloaded no equilibrium | Configure total load above maximum displaced water in bounds | Solver returns `NO_EQUILIBRIUM_SINKING`, never fabricates a target, never throws, and respects lower bound/hold behavior |
| A11 | No water/sealed columns | Ship has no valid water sample or sealed columns | Returns `NO_EQUILIBRIUM_NO_WATER`; pose is not forced to an arbitrary height |
| A12 | Bounds/tolerance | Candidate crossing lies just inside/outside configured bounds; perturb load around tolerance | Solver never samples outside bounds; accepted candidate meets mass/draft tolerance; near-threshold result is deterministic |
| A13 | Monotonicity guard | Synthetic surface produces a sampled displaced-volume increase | Solver detects non-monotonicity, avoids invalid interpolation, and returns stable first-candidate/unstable behavior |
| A14 | Footprint-dependent draft | Two stable ships with comparable load delta but different effective footprint areas | Measured `Δdraft` follows `Δmass/(waterDensity×footprint)` within documented discrete tolerance; larger footprint changes less |
| A15 | Restart at floated pose | Float, persist pose, restart/reconcile with no riders | Restored pose is the persisted floated pose; block mass is recomputed from current config; rider mass starts at zero |
| A16 | Reload recomputation | Float ship, change material density/default or player mass, reload successfully | Next tick uses new config and recalculates target; no stale equilibrium or partial config table |
| A17 | Reload failure | Supply invalid density on reload | Prior valid config remains active; no partial values are installed; ship remains operational per reload contract |
| A18 | Disassembly at authoritative anchor | Fractional floated pose with known `floor(y)`; disassemble | Blocks restore at `origin + anchorDy()`; no stale build-site restoration and no fractional rounding drift |
| A19 | Persistence compatibility | Load legacy `ships.json` lacking optional pose/buoyancy | Existing defaults (`y=0`, buoyancy enabled) remain; no new mass fields are required |
| A20 | Runtime cleanup | Remove/disassemble/rollback/disable with rider state present | Per-ship velocity, equilibrium status, and rider snapshot are cleared; no rider data leaks to another ship |

### Live acceptance matrix

A real Paper server run is required in addition to automated tests. Record date, server build, config, world, and observed result for each:

| Live ID | Scenario | Expected observation |
|---|---|---|
| L1 | Assemble equal-volume light/heavy-material hulls in still water | Heavy hull settles visibly deeper while both remain stable when each has a solution |
| L2 | Add/remove a tracked player on a broad raft | Boarding increases draft on the next physics boundary; leaving reverses it without passenger teleport failure breaking motion |
| L3 | Overload raft beyond bounded displacement | Ship reports/holds documented no-equilibrium sinking behavior and does not jump to an arbitrary waterline |
| L4 | Restart after floating | Runtime reconstructs at persisted floated pose, with no phantom riders; subsequent disassembly uses authoritative anchor |
| L5 | Reload density/player-mass config | Following tick visibly converges toward the new equilibrium; invalid reload leaves previous behavior intact |
| L6 | Irregular footprint / shallowest-column water | Draft follows measured effective footprint and shallowest-surface rule; no horizontal movement or rotation is introduced |

Live evidence is not implied by this draft. A failed or unavailable live check keeps the corresponding acceptance item open.

## Separate implementation plan (not approved)

- [ ] Add validated namespaced material-density and player-mass configuration without changing `ships.json`.
- [ ] Add immutable config snapshot and aggregate block-mass calculation.
- [ ] Add tracked-player snapshot/count integration at physics boundaries with best-effort carry preservation.
- [ ] Add bounded deterministic monotonicity-guarded equilibrium solver and explicit result states.
- [ ] Wire reload, lifecycle clearing, restart, and disassembly behavior.
- [ ] Add automated acceptance cases A1–A20.
- [ ] Execute live acceptance L1–L6 and record evidence separately.
- [ ] After approval and implementation, update living specs and commit through the normal review gate.

## Approval gate

- [x] User approved this complete contract on 2026-08-16.
- [x] Lower-bound/no-equilibrium descent and diagnostic observability are deterministic.
- [ ] Separate implementation plan approved.
- [ ] Implementation complete.

This approved design may now be promoted into living-spec decisions. It does not authorize physics implementation without a separate approved plan.
