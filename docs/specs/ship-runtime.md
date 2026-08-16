# Ship Runtime — Living Spec

> Status: active
> Last updated: 2026-08-16
> Owners: jlo

## Intent

Assembled ships exist in the world as runtime artifacts: one `BlockDisplay` per captured block plus an entity collision hull — while `ships.json` stays the only persistence authority. This domain composes rendering, collision, and entity-carry into one transactional lifecycle (spawn / move / remove) and reconciles runtime state on restart.

Success looks like: exact visual alignment to canonical block corners, player-solid hulls without placed blocks, drift-free repositioning, atomic all-or-nothing moves, and deterministic reconstruction from persistence.

## Boundaries

### In scope

- `ShipTransform` consumption: displays at visual corners, hulls at collision anchors
- `ShipRenderer` / `RenderSurface` / `BukkitShipRenderer` — per-block BlockDisplays
- `CollisionHull` / `CollisionVolume` / `CollisionVolumeManager` / `BukkitCollisionVolumeManager` — invisible Shulker hulls
- `ShipRuntime` / `ShipRuntimeImpl` — spawn/move/remove transactions, rollback
- `ShipEntityCarrier` / `BukkitShipEntityCarrier` / `BukkitShipRiderTracker` / `TopSurfaceIndex` — rider carry
- `ShipServiceImpl` assembly/disassembly/load reconciliation wiring
- Stale-entity sweeps and restart reconstruction

### Out of scope / non-goals

- Physics (buoyancy drives `ShipRuntime.move`; see `buoyancy`)
- Ship data model and persistence format (see `ship-model`)
- Command surface (see `commands`)
- Horizontal navigation, rotation, passenger sitting, damage
## Invariants

- Displays use **visual** projection: `origin + pose.y + relative`, exact block corner — no implicit `+0.5`.
- Hulls spawn at `collisionAnchor` (visual corner + 0.5 on X/Z). `move` teleports every volume on every move (no anchor-change guard today); `rollback` moves all volumes back to the old anchor.
- `ShipRuntimeImpl` field order is renderer, collisions, carrier; **spawn order is collisions first, then renderer**. Operation order on move depends on direction:
  - upward: reposition displays → carry riders → move collisions
  - downward/equal: reposition displays → move collisions → carry riders
- Runtime failure handling is scoped to `ShipRuntimeException`: spawn rolls back (renderer cleanup, collision removal, suppressed cleanup failures) and rethrows; move rolls back (collisions, pose, reversed carrier on rising path, renderer). Unchecked adapter/entity failures are not caught and may bypass cleanup; `remove`/`removeAll`/collision removal propagate directly without suppression.
- The target under Next is to normalize adapter/runtime failures to `ShipRuntimeException` and guarantee that spawn, move, and reconciliation either complete or restore their pre-operation model/runtime/world state. Best-effort rider transport is explicitly excluded from ship rollback. Tasks 4–6 promote this target into the invariant and Current only after their regression tests pass.
- Collision volumes are invisible, invulnerable, silent, no-AI, gravity-off, collidable Shulkers with `peek=0.0f`, `persistent=false`, PDC `collision-owner` + `collision-owner-block`, scoreboard tag `ships-collision-<uuid>`. Displays are non-persistent entities tagged with `ship-id` + `ship-id-block` PDC keys; identity is model-derived, never reverse-engineered from entity locations.
- Spawn is all-or-nothing per ship for failures normalized as `ShipRuntimeException`; unchecked adapter/entity failures are not caught and may leave partial entities.
- Restart: `ShipServiceImpl.loadAll` calls `runtime.removeAllTagged()` then deterministically respawns from models, cleaning partial entities on normalized `ShipRuntimeException` failure and throwing `IllegalStateException` (naming the failing ship); `ShipsPlugin` catches it, disables the plugin, and runs unguarded disable cleanup (`removeAllRuntime` + `removeAllTagged`). The initial sweep sits outside the try — an unchecked sweep failure escapes without cleanup.
- Riders: carry is best-effort — a failed teleport never rolls back the ship move.
- No barrier/deck blocks are placed by production code (deck package is legacy; see debt below).

## Implementation guidance

- Domain interfaces (`ShipRenderer`, `CollisionVolumeManager`, `CollisionVolume`, `ShipRuntime`, `ShipEntityCarrier`) never import Bukkit; Bukkit adapters live in `dev.jlo.ships.bukkit`.
- PDC identity uses two distinct key families. Renderer displays use `ShipsPlugin.shipKey()` (`ship-id`) and derive `ship-id-block` from that key. Collision Shulkers use the separately wired `collision-owner` key and derive `collision-owner-block` from it. Each adapter's stale sweep and remove path must remain symmetric with its own spawn-time PDC tagging; do not assume or introduce a shared base-key derivation.
- Reposition pairs tagged displays by PDC block key and recomputes from the model (IdentityHashMap guard); never teleport by incremental offsets. The collision manager tracks volumes in a `HashMap` per ship (no identity guard; movement side-effect order unspecified) — renderer pairs by key, collision volumes by relative map key.
- Buoyancy callers: buoyancy changes pose then calls `runtime.move(oldY,newY)`; runtime failure must restore the old pose.
- Tests: per-package JUnit covering ordering (spawn, move up/down, rollback, carrier reversal), drift-free reposition, hull determinism (3×3×3 → 26 exposed), and restart reconciliation. `BukkitCollisionVolumeManagerTest` is compile-only (no flag/anchor/rollback/tag behavior) — behavioral entity tests are a gap.
- `removeAllTagged` only runs when adapters are Bukkit concrete classes — keep it a runtime-time capability check, not a config flag.

## Current

- [x] Canonical drift-free rendering (block-corner alignment; negative-pose coverage)
- [x] Deterministic exposed hull (`CollisionHull.exposedBlocks`, lexicographic; `topExposedBlocks` for carrier)
- [x] Production Shulker hull attached to spawn/move/remove lifecycle
- [x] Transactional spawn with rollback (collisions → renderer; cleans both on `ShipRuntimeException`; unchecked failures may bypass cleanup)
- [x] Direction-ordered move transaction with rider reversal on rollback
- [x] `ShipRuntime.move(oldY, newY)` multi-block and repeated-bob support
- [x] Persistent rider tracking via Bukkit events (move/spawn/death/quit/teleport/world-change/vehicle; no disable/untrack hook wired — tracker registered after loadAll)
- [x] Carry: Players get relative vertical velocity (momentum preserved), others teleport `(0,delta,0) PLUGIN` cause; rejected/failed teleports are swallowed (best-effort, no rollback)
- [x] Restart reconciliation: stale tagged entities swept, models respawned; `IllegalStateException` disables plugin (sweep outside try; unchecked sweep failures unguarded — see Next)
- [x] `removeAllRuntime()` + `removeAllTagged()` on disable (no save)

### Current notes

- Live player-movement verification of Shulker collision was **never recorded**; the dated spike result (`docs/superpowers/results/2026-08-14-non-block-collision-spike.md`) is BLOCKED, yet production integrated the hulls (commits `3b44b46+`). Geometry correctness is unverified — this is the top acceptance gap.
- Rider carry is **kinematic and best-effort, vertical only**: `ShipEntityCarrier.carry(ship, oldY, newY)` updates tracked riders by `delta` during a vertical move — players get added relative vertical velocity (momentum preserved), non-player entities are teleported `(0, delta, 0)` with PLUGIN cause. There is no attachment/constraint concept. "Contained bodies move with the ship" holds today only for vertical deltas and only while the move transaction runs. Horizontal carry requires a generalized carrier (delta vector) or true attachment — no such mechanism or state exists.

## Next

- [ ] Record live collision acceptance: stand on exposed tops, hull-side blocking, no pass-through, six-directional face checks
- [ ] Remove dead `deck/` package (`DeckManager`, `DeckSurface`, `BukkitDeckSurface`) and its tests (compiled but unwired) once legacy helper references are dropped
- [ ] Fix stale Javadocs/wording: `ShipService.removeAllRuntime` "entities and barriers"; `ShipServiceImplTest` unused `NoopDeck` helper; plugin.yml "walkable decks" description
- [ ] Harden reconciliation failure paths: `loadAll` sweep outside try, unguarded disable cleanup (unchecked sweep failures escape without cleanup)
- [ ] Harden spawn/move cleanup: catch unchecked adapter/entity failures and normalize them to `ShipRuntimeException`; guarantee spawn, move, and reconciliation restore pre-operation model/runtime/world state. Best-effort rider transport remains outside ship rollback. Tasks 4–6 promote this target into Current only after regression tests pass.
- [ ] Guard collision `move` so volumes only teleport when the authoritative anchor changes (currently unconditional)
- [ ] Add behavioral collision-manager tests (flags, anchors, PDC/scoreboard tags, rollback, tag cleanup) — `BukkitCollisionVolumeManagerTest` is compile-only
- [ ] Document persistence coupling: `ShipServiceImpl.tick` persists once iff any ship moved, respecting only the global `buoyancy-enabled` scheduler gate
- [ ] Resolve carrier seed-vs-update pose window: `track(ship, oldY)` seeds at old pose but the tracker update loop overlaps at current pose

## Future

- [ ] Horizontal movement with runtime carry — prerequisite stack (nothing exists today):
  - Generalized `ShipRuntime.move` accepting a horizontal delta + yaw (current `move(oldY, newY)` is vertical-only)
  - Swept-area path clearance for a 2D footprint across intermediate positions and rotations (vertical-only `pathClear` today)
  - Rotation handling: BlockDisplay `Transformation` matrices (per-block corner alignment breaks under yaw); Shulker hull volumes are axis-aligned boxes — re-derive exposed hull per orientation
  - Generalize rider carry beyond vertical deltas; x/z-keyed top-surface grid is invalidated by rotation. Decide kinematic carry (teleport by ship delta — current model) vs true attachment (contained bodies inherit ship velocity/position continuously)
  - Persistence: pose gains x/z + yaw (schema extension, backward compat)
  - Chunk management: horizontal travel crosses chunk boundaries; dated design forbids unloaded-chunk traversal
- [ ] Ride passengers properly (seats) instead of best-effort carry
- [ ] Multi-world support beyond primary world binding

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | Living specs in `docs/specs/`; dated docs stay in `docs/superpowers/` as history | User directive |
| 2026-08-16 | Runtime is bound to the primary Bukkit world; cross-world support remains Future | Current assembly/runtime wiring uses the primary world; command resolution may identify another world but assembly rejects it |
| 2026-08-15 | Carry teleports players with relative velocity, other entities with PLUGIN-cause teleport | Preserve rider momentum; avoid teleport-cause fallout |
| 2026-08-15 | Riders tracked persistently via events, grid index built once per ship | Avoid `getNearbyEntities` on every move |
| 2026-08-14 | Shulker hulls integrated despite BLOCKED spike evidence | Followed runtime-transform plan; acceptance gap recorded — revisit live |
| 2026-08-14 | Barriers removed from production; deck code left in tree | Clean cutover deferred; debt tracked here |

## Open questions

- [ ] Is Shulker hitbox geometry acceptable for the intended hulls (over-blocking vs gaps)? (blocks the "record live acceptance" item)
- [ ] Should collision volumes use fractional `move(double…)` anchors given docs said integer-only?
