# Ship runtime

## Boundaries

The runtime composes renderer, collision, and entity-carrier adapters for vertical ship movement. It owns runtime entity reconciliation and cleanup, while persistence and command policy remain outside this domain.

## Invariants

- Hulls spawn at `collisionAnchor` (visual corner + 0.5 on X/Z). Fractional same-anchor moves do not teleport volumes; crossing an integer anchor moves every volume. `rollback` moves all volumes back to the old anchor when the anchor changed.
- `ShipRuntimeImpl` field order is renderer, collisions, carrier; **spawn order is collisions first, then renderer**. Operation order on move depends on direction:
  - upward: reposition displays → carry riders → move collisions
  - downward/equal: reposition displays → move collisions → carry riders
- Adapter failures in renderer and collision operations are normalized to `ShipRuntimeException` with the operation and ship ID where available; existing `ShipRuntimeException` instances are preserved without double wrapping. Only `RuntimeException` is normalized; `Error` remains uncaught.
- Collision spawn publishes the per-ship volume map only after every exposed volume is created. A failed partial spawn removes every locally created volume, and cleanup failures are suppressed on the primary failure.
- Runtime failure handling remains scoped to normalized `ShipRuntimeException`: spawn rolls back (renderer cleanup, collision removal, suppressed cleanup failures) and rethrows; move rolls back (collisions, pose, reversed carrier on rising path, renderer). Best-effort rider transport is explicitly excluded from ship rollback.
- Collision volumes are invisible, invulnerable, silent, no-AI, gravity-off, collidable Shulkers with `peek=0.0f`, `persistent=false`, PDC ship-id + relative block key, scoreboard tag `ships-collision-<uuid>`.
- Displays are non-persistent entities tagged with ship UUID + relative `x,y,z` PDC key; identity is model-derived, never reverse-engineered from entity locations.
- Spawn is all-or-nothing per ship for failures normalized as `ShipRuntimeException`; partial collision entities are cleaned and no partial map state is published.
- Riders: carry is best-effort — a failed teleport never rolls back the ship move.
- No barrier/deck blocks are placed by production code; Shulker collision hulls provide runtime collision.

## Decisions

- The former barrier-backed deck implementation was removed after the Shulker hull became the production collision path; the historical design and spike records remain unchanged.

## Current

- [x] Canonical drift-free rendering (block-corner alignment; negative-pose coverage)
- [x] Deterministic exposed hull (`CollisionHull.exposedBlocks`, lexicographic; `topExposedBlocks` for carrier)
- [x] Production Shulker hull attached to spawn/move/remove lifecycle
- [x] Adapter/runtime normalization for renderer and collision remove, removeAll, spawn, move, rollback, and cleanup paths; existing SREs are preserved and `Error` remains uncaught
- [x] Transactional spawn with normalized adapter rollback (collisions → renderer); partial collision entities are cleaned and cleanup failures suppressed
- [x] Direction-ordered move transaction with rider reversal on rollback
- [x] `ShipRuntime.move(oldY, newY)` multi-block and repeated-bob support
- [x] Persistent rider tracking via Bukkit events
- [x] Carry: Players get relative vertical velocity; other entities teleport vertically; rejected/failed teleports are swallowed (best-effort, no rollback)
- [x] Restart reconciliation: stale tagged entities swept, models respawned
- [x] `removeAllRuntime()` + `removeAllTagged()` on disable (no save)
- [x] Reconciliation is one cleanup boundary: store load, initial tagged-entity sweep, and deterministic per-ship spawn are covered by one `RuntimeException` boundary (but not `Error`). On failure, every already-spawned ship is removed, a final tagged-entity sweep is attempted, and the model registry is cleared even when individual cleanup actions fail. Cleanup failures are suppressed on the primary cause, and the thrown `IllegalStateException` identifies the failing phase and ship (or `unknown` when no ship is active). Store-load failures follow the same path.
- [x] Plugin disable attempts registered-runtime removal and tagged-entity removal independently, logs each cleanup failure, and never saves persistence during disable.

Rider tracking is an explicit runtime lifecycle: a successfully spawned ship is registered with
the carrier using its committed pose, removal untracks that ship, and complete runtime cleanup
clears all carrier state. Carry and tracker overlap checks use the pose supplied by the move
transaction; event processing must not reread a mutable ship pose for the same move window.
- [ ] Record live collision acceptance: stand on exposed tops, hull-side blocking, no pass-through, six-directional face checks
- [ ] Remove dead `deck/` package and stale wording once legacy references are dropped
- [x] Guard collision `move` so volumes only teleport when the authoritative anchor changes
- [ ] Add behavioral collision-manager tests for flags, anchors, PDC/scoreboard tags, rollback, and tag cleanup
- [ ] Document persistence coupling: `ShipServiceImpl.tick` persists once iff any ship moved, respecting only the global buoyancy-enabled scheduler gate

## Future

- [ ] Horizontal movement with runtime carry
- [ ] Chunk management for horizontal travel
