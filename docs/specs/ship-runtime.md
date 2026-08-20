# Ship Runtime — Living Spec

> Status: active
> Last updated: 2026-08-19
> Owners: jlo

## Intent

Assembled ships exist in the world as runtime artifacts: one `BlockDisplay` per captured block plus an entity collision hull — while `archimedes.json` stays the only persistence authority. This domain composes rendering, collision, and entity-carry into one transactional lifecycle (spawn / move / remove) and reconciles runtime state on restart.

Success looks like: exact visual alignment to canonical block corners, player-solid hulls without placed blocks, drift-free repositioning, atomic all-or-nothing moves, and deterministic reconstruction from persistence.

## Boundaries

### In scope

- `ShipTransform` consumption: displays at visual corners, hulls at collision anchors
- `ShipRenderer` / `RenderSurface` / `BukkitShipRenderer` — per-block BlockDisplays plus tessellated cloth plates
- `SailMesh` / `SailPiece` / `SailCell` — Paper-free cloth region → thin-plate series
- `CollisionHull` / `CollisionVolume` / `CollisionVolumeManager` / `BukkitCollisionVolumeManager` — invisible Shulker hulls
- `ShipRuntime` / `ShipRuntimeImpl` — spawn/move/remove transactions, rollback
- `ShipEntityCarrier` / `BukkitShipEntityCarrier` / `BukkitShipRiderTracker` / `TopSurfaceIndex` — rider carry
- `ShipServiceImpl` assembly/disassembly/load reconciliation wiring
- Stale-entity sweeps and restart reconstruction

### Out of scope / non-goals

- Physics (buoyancy drives `ShipRuntime.move`; see `buoyancy`)
- Ship data model and persistence format (see `ship-model`)
- Command surface (see `commands`)
- Rotation, passenger sitting, damage
- Arbitrary GPU meshes, resource-pack authoring, or a model engine (see Future)

## Invariants

- Displays use **visual** projection: `origin + pose.y + relative`, exact block corner — no implicit `+0.5`.
- Hulls spawn at `collisionAnchor` (visual corner + 0.5 on X/Z). Collision volumes follow the fractional pose so the solid deck matches the picture. Rollback restores every moved volume to the old anchor.
- `ShipRuntimeImpl` field order is renderer, collisions, carrier; **spawn order is collisions first, then renderer**. Operation order on move: reposition displays, carry riders by the full pose delta `(dx,dy,dz)`, then move collisions. Sliding the Shulker hull first on XZ/down ticks shoves standees off the deck before carry can glue them. Rollback reverse-carries and restores the full previous pose, not Y-only.
- Adapter failures in renderer and collision operations are normalized to `ShipRuntimeException` with operation and ship context where available; existing `ShipRuntimeException` instances are preserved. Only `RuntimeException` is normalized; `Error` remains uncaught.
- Carrier tracking is explicit: successful spawn tracks at the committed pose, remove untracks, and every runtime cleanup path clears tracker state.
- Rider seed and overlap checks use the move transaction's supplied pose basis `(x,y,z)`; the top-surface index is stored at pose zero and shifted by that basis. Tracker updates do not read a concurrently changing pose for that transaction.
- No barrier/deck blocks are placed by production code; Shulker collision hulls provide runtime collision.
- The server cannot upload an arbitrary triangle mesh. Ship visuals are `BlockDisplay` entities the client already knows how to draw.

## Implementation guidance

- Domain interfaces live in `:api` and never import Bukkit except the documented `compileOnly` leaks; Paper-free runtime composition (`ShipRuntimeImpl`, `CollisionHull`) lives in `:common`; Bukkit adapters live in `:paper` under `dev.mintychochip.archimedes.bukkit`.
- PDC identity uses distinct renderer (`ship-id`) and collision (`collision-owner`) key families; stale sweeps and remove paths remain symmetric with their spawn-time tags.
- Reposition pairs hull displays by PDC block key and sail plates by tessellation index, then recomputes from the model. Collision volumes stay keyed by relative block position.
- Hull picture stays one untransformed `BlockDisplay` per non-cloth captured block (`setBlock` only). Cloth (`*_wool`, `*_banner`, `*_wall_banner`) is a tessellated sheet of transformed `BlockDisplay` plates from `SailMesh` — not one cube per cloth cell, and not a resource-pack / `ItemDisplay` model. Other curved looks (`ItemDisplay` + pack model, or a later bone engine) remain Future overlays. See `docs/superpowers/specs/2026-08-17-curved-mesh-rendering-review.md`.
- Torn cloth leaves the sheet (`SailMesh.cellsOf(intactBlocks)`) and spawns a separate untransformed `BlockDisplay` ragdoll. `ShipRuntime.spawnClothRagdoll` / `moveClothRagdoll` drive it; remaining sail plates retessellate on tear. Collision hulls for torn cells are still spawned at assembly (refresh is Next).
- Visual `BlockDisplay`s (hull and sail plates) get `setTeleportDuration(1)` at spawn so the client interpolates 20 TPS teleports. Reposition still teleports to the model visual corner and does not clear that duration. Collision Shulkers snap to the same fractional pose.
- Buoyancy callers change pose then call `runtime.move(oldY,newY)`; runtime failure restores the old pose.
- `removeAllTagged` is a runtime capability: `ShipRuntimeImpl` delegates to `ShipRendererLike.removeAllRuntime()` and `CollisionVolumeManager.removeAllTagged()`, which Bukkit adapters implement as tagged-entity sweeps.

## Current

- [x] Canonical drift-free rendering and deterministic exposed hulls
- [x] Production Shulker hull attached to spawn/move/remove lifecycle
- [x] Transactional spawn and direction-ordered move rollback
- [x] Persistent rider tracking and best-effort XYZ carry (players and other entities teleport by the hull delta; a normal jump stays on the deck)
- [x] Carry runs before collision on every move so a sailing hull cannot shove standees off the deck
- [x] Adapter/runtime normalization and continued multi-entity cleanup
- [x] Restart reconciliation: store load, initial tagged-entity sweep, and deterministic spawn are one `RuntimeException` boundary. On failure, every spawned ship is removed, a final tagged-entity sweep is attempted, the model registry is cleared even when individual cleanup actions fail, and cleanup failures are normalized to `ShipRuntimeException` and suppressed on the primary cause. One `IllegalStateException` identifies the failing phase and ship (`unknown` if none is active). `Error` remains uncaught. Store-load failures follow the same cleanup boundary.
- [x] Plugin disable independently attempts registered-runtime removal and tagged-entity removal, logs each failure, and never saves persistence during disable.
- [x] Review: Paper cannot stream a GPU mesh; hulls stay `BlockDisplay` per block; curves are overlay options only (`docs/superpowers/specs/2026-08-17-curved-mesh-rendering-review.md`)
- [x] Cloth regions render as a tessellated series of thin transformed `BlockDisplay` plates (`SailMesh`); hull cells stay one untransformed cube; sail plates tag with the ship, move on reposition, and vanish on tagged remove
- [x] Visual BlockDisplays interpolate pose teleports (`setTeleportDuration` ≥ 1 tick); collision Shulkers do not
- [x] Torn cloth spawns a `BlockDisplay` ragdoll that is teleported and reoriented each debris step; remaining sail plates retessellate from `intactBlocks`
- [x] Captured dispenser cannons use exactly one attached stone-button display as the interaction control; owner/operator clicks launch a non-incendiary bounded vanilla projectile from the current transformed muzzle with a runtime-only two-second cooldown and no ammunition or item model.

## Next

- [ ] Drop collision volumes for torn cloth when a cell snaps
- [ ] Record live collision acceptance: stand on exposed tops, hull-side blocking, no pass-through, six-directional face checks
- Live attempt on 2026-08-16 remained blocked by an occupied server port and no connected Minecraft client; observed startup evidence and the exact reproduction matrix are recorded in `docs/superpowers/results/2026-08-16-spec-alignment-acceptance.md`. Automated hull tests do not satisfy this item.
- [x] Remove dead package and stale wording; historical design records retain the original barrier-deck decision.
- [x] Collision `move` follows the fractional pose so a standing player has a deck under their feet
- [x] Add behavioral collision-manager tests for flags, anchors, PDC/scoreboard tags, rollback, and tag cleanup
- [x] Document persistence coupling: `ShipServiceImpl.tick` persists once iff any ship moved; direct `tick()` still executes when the global scheduler is disabled, while the disabled scheduler prevents automatic calls.

## Future

- [x] Horizontal movement with runtime carry
- [ ] Chunk management for horizontal travel
- [ ] Decorative curved parts as an overlay (not hull replacement): `ItemDisplay` + resource-pack cuboid model, or a later bone/item-display engine. Cloth tessellation is Current.

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | Living specs in `docs/specs/`; dated docs stay in `docs/superpowers/` as history | User directive |
| 2026-08-16 | Runtime is bound to the primary Bukkit world; cross-world support remains Future | Current assembly/runtime wiring uses the primary world |
| 2026-08-16 | Split plugin into Gradle `api` / `common` / `paper` | Public types vs Paper-free impls vs plugin adapters; `paperweight` only on `paper` |
| 2026-08-15 | Carry is vertical and best-effort | Preserve rider momentum without turning transport into transaction failure |
| 2026-08-17 | Carry is XYZ and best-effort | User: standing on a sailing ship must stay on it like a vehicle |
| 2026-08-17 | Players teleport with the hull; jump stays a rider; shulkers follow fractional pose | Velocity carry left standers behind and cancelled jumping; integer-only hulls slid out from under feet |
| 2026-08-17 | Every move carries riders before collision volumes | XZ/down used to slide Shulkers first; vanilla unstuck unboarded standees and they fell off |
| 2026-08-14 | Shulker hulls integrated despite blocked live spike evidence | Acceptance gap remains recorded |
| 2026-08-17 | No GPU mesh upload; hull stays voxel `BlockDisplay`; curves are Future overlays | Paper protocol has no triangle-mesh packet; the ship is the scanned build |
| 2026-08-17 | Cloth sails are tessellated `BlockDisplay` plates from the captured region | User asked for a series of block displays from a 3D cloth region before any resource pack |
| 2026-08-17 | Visual displays use 1-tick teleport interpolation; Shulkers snap | 20 TPS teleports look stuttery; duration > 1 lags the picture behind collision |
| 2026-08-19 | Ship cannons derive from captured dispenser/button blocks and fire through tagged BlockDisplay interaction | Assembled ships have no live blocks; renderer ship/block PDC tags preserve a physical control without custom item models |
| 2026-08-19 | Torn cloth ragdolls as a `BlockDisplay` cube, not a remaining sail plate | User: the piece that comes off should look like a block and tumble |
