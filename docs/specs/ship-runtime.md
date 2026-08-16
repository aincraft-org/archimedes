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
- Hulls spawn at `collisionAnchor` (visual corner + 0.5 on X/Z). `move` teleports every volume on every move; rollback moves all volumes back to the old anchor.
- `ShipRuntimeImpl` field order is renderer, collisions, carrier; **spawn order is collisions first, then renderer**. Operation order on move: upward repositions displays, carries riders, then moves collisions; downward/equal repositions displays, moves collisions, then carries riders.
- Adapter failures in renderer and collision operations are normalized to `ShipRuntimeException` with operation and ship context where available; existing `ShipRuntimeException` instances are preserved. Only `RuntimeException` is normalized; `Error` remains uncaught.
- Carrier tracking is explicit: successful spawn tracks at the committed pose, remove untracks, and every runtime cleanup path clears tracker state.
- Rider seed and overlap checks use the move transaction's supplied pose basis; tracker updates do not read a concurrently changing pose for that transaction.
- No barrier/deck blocks are placed by production code (deck package is legacy).

## Implementation guidance

- Domain interfaces never import Bukkit; Bukkit adapters live in `dev.jlo.ships.bukkit`.
- PDC identity uses distinct renderer (`ship-id`) and collision (`collision-owner`) key families; stale sweeps and remove paths remain symmetric with their spawn-time tags.
- Reposition pairs tagged displays by PDC block key and recomputes from the model; collision volumes are keyed by relative block position.
- Buoyancy callers change pose then call `runtime.move(oldY,newY)`; runtime failure restores the old pose.
- `removeAllTagged` is a runtime capability and is invoked only by concrete Bukkit-backed cleanup wiring.

## Current

- [x] Canonical drift-free rendering and deterministic exposed hulls
- [x] Production Shulker hull attached to spawn/move/remove lifecycle
- [x] Transactional spawn and direction-ordered move rollback
- [x] Persistent rider tracking and best-effort vertical carry
- [x] Adapter/runtime normalization and continued multi-entity cleanup
- [x] Restart reconciliation: store load, initial tagged-entity sweep, and deterministic spawn are one `RuntimeException` boundary. On failure, every spawned ship is removed, a final tagged-entity sweep is attempted, the model registry is cleared even when individual cleanup actions fail, and cleanup failures are normalized to `ShipRuntimeException` and suppressed on the primary cause. One `IllegalStateException` identifies the failing phase and ship (`unknown` if none is active). `Error` remains uncaught. Store-load failures follow the same cleanup boundary.
- [x] Plugin disable independently attempts registered-runtime removal and tagged-entity removal, logs each failure, and never saves persistence during disable.

## Next

- [ ] Record live collision acceptance: stand on exposed tops, hull-side blocking, no pass-through, six-directional face checks
- [ ] Remove dead `deck/` package and stale wording once legacy references are dropped
- [ ] Guard collision `move` so volumes only teleport when the authoritative anchor changes
- [ ] Add behavioral collision-manager tests for flags, anchors, PDC/scoreboard tags, rollback, and tag cleanup
- [ ] Document persistence coupling: `ShipServiceImpl.tick` persists once iff any ship moved

## Future

- [ ] Horizontal movement with runtime carry
- [ ] Chunk management for horizontal travel

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | Living specs in `docs/specs/`; dated docs stay in `docs/superpowers/` as history | User directive |
| 2026-08-16 | Runtime is bound to the primary Bukkit world; cross-world support remains Future | Current assembly/runtime wiring uses the primary world |
| 2026-08-15 | Carry is vertical and best-effort | Preserve rider momentum without turning transport into transaction failure |
| 2026-08-14 | Shulker hulls integrated despite blocked live spike evidence | Acceptance gap remains recorded |
