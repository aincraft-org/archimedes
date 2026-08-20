# Streamed Collision Hull Design

## Intent

Keep player-solid ship hulls as 1×1×1 Shulker cubes on exposed cells, but stop paying the full surface area on every client and every move. Spawn a cube only when some observer is close enough to hit it, share that cube across observers, hide it from everyone else, and despawn it when the last observer leaves.

Default play is this streamed path (**B**). Today’s full spawn (**A**) remains as a control so the two can be compared on the same ship.

## Player experience

Standing on a deck and walking into a hull wall still hits a cube whose top and sides match the pictured block. A player only receives the cubes whose boxes they can actually reach. A second player at the other end of the ship does not pay for the first player’s neighborhood. An empty ship has no collision entities until something approaches.

## A/B

| | A — control (today) | B — streamed (default) |
|---|---------------------|-------------------------|
| Spawn at assemble | every exposed cell | index only, zero entities |
| Live Shulkers | `exposed` | cells within share range of at least one observer |
| Client packets | all volumes | only cells that player observes |
| Move | teleport every volume | teleport live volumes |
| Geometry | 1×1×1 at `collisionAnchor` | same cube, subset of cells |

**Hypothetical fixture:** 1-thick box hull, outer 20×8×5, one player mid-deck, enter range 4. A live count equals exposed cells. B live count equals the neighborhood. B with no observers is 0. Every B cell is a member of A’s exposed set.

**Pass:** B live ≤ A live; B empty-ship live = 0; in-range stand and side contact still work on B. Unit tests prove the counts. Live stand/side contact stays the existing ship-runtime acceptance item and is not claimed from unit tests.

Mode is per-ship, runtime-only, not written to `archimedes.json`. Default is B. An operator may flip the targeted ship between A and B. Inspect prints `collision=A|B live=N exposed=M visibleToYou=K`.

## Why cubes, not face types

A closed Shulker is already a walkable top and a blocking side. Player-vs-entity collision is client-predicted, so the client must already know the hitbox. Minecraft does not give a collidable thin plate or arbitrary AABB that the client will block against. Face-typed volumes and greedy face merges are out of scope. If a later neighborhood is still too dense, pack only solid 2ⁿ cube regions that actually exist in the hull — never one AABB over a U-shaped deck.

## Components

- `ExposedCellIndex` (`:common`) — exposed cells at pose zero, same pose-shift pattern as `TopSurfaceIndex`. Query returns cells whose 1×1×1 box is within AABB-to-AABB edge distance of an observer box. Enter range 4, leave range 6 (hysteresis). Also exposes the union ship bounds.
- `CollisionVolumePool` (`:common`) — relative cell → observer ids. First observer is a spawn. Additional observers share. Last observer is a despawn. Distinguishes player observers (need packets) from non-player observers (keep the cube alive only).
- `CollisionVolumeManager` — owns index + pool. Mode A keeps today’s full spawn, globally visible. Mode B uses the pool. `spawn` in B builds the index and seeds observers; it does not pre-spawn the hull. `move` teleports live volumes and reconciles observers because the hull moved. `rollback` / `remove` / tagged sweeps stay as today.
- Observer sampling (Paper adapter) — entities whose AABB intersects the ship bounds expanded by leave range. Players are spawn+show observers. Items and mobs are spawn-only observers so unattended cargo does not fall through. Reconcile on ship move, observer block-boundary cross, spawn, quit, teleport, and death — not every `PlayerMoveEvent`.
- Carry is unchanged. `BukkitShipRiderTracker` / `TopSurfaceIndex` still mean “standing on a top for teleport-carry,” not “could hit the hull.”

Shulker flags, PDC `collision-owner` / block key, scoreboard tags, and `collisionAnchor` (visual + 0.5 on X/Z, Y unchanged) do not change.

## Data flow

1. Assemble → `CollisionHull.exposedBlocks` → index. Mode A spawns all cubes. Mode B spawns none, then samples nearby entities and fills the pool.
2. Observer in range of a cell → pool add. If the cell is new, spawn one invisible Shulker at the canonical anchor with `visibleByDefault(false)`. If the observer is a player, `showEntity`.
3. Second observer of the same cell → refcount++, share the same entity, `showEntity` if they are a player.
4. Observer leaves (edge distance > leave range) → hide if player, refcount--. At 0, remove the entity.
5. Move → teleport live volumes to new anchors, then reconcile (non-carried entities may have entered or left range).
6. Remove / disable → drop pool and tagged entities as today.

Need test is AABB-to-AABB distance to **cell edges**, not ship origin and not box centers. Distance is 0 when the boxes overlap; otherwise the Euclidean length of the positive axis separations. A cell is entered at distance ≤ 4 and left at distance > 6.

## Error handling

Same transactional rules as today. Partial B spawn during reconcile cleans up the cubes it created and surfaces `ShipRuntimeException`. Failed move rolls back live volumes. Mode switch A→B despawns cubes no observer needs. B→A spawns any missing exposed cells as globally visible and clears per-player hiding. Adapter failures still normalize to `ShipRuntimeException`. `Error` stays uncaught.

## Configuration and command

Ranges and the default mode are named constants in the collision domain (enter 4, leave 6, default B). They are not added to `ShipConfig` in this slice. `/arch collision a|b` (alias `full|streamed`) is operator-only, targets the nearby hull, and switches that ship’s runtime mode. `/arch inspect` appends the collision line from the volume manager; it does not enlarge `ShipInspection`.

## Testing

Paper-free:

- Index: in range, out of range, overlapping boxes, hysteresis (still held between 4 and 6, dropped past 6).
- Pool: first observer spawns, second shares, last despawns, player vs non-player observer actions.
- A/B fixture: same 20×8×5 shell and mid-deck observer AABB. A live = exposed count. B live = neighborhood. B with no observers = 0. Every B cell ∈ A exposed set. B live ≤ A live.

Paper adapter:

- Mode B spawn creates no Shulkers until an observer is supplied.
- Seeded player observer spawns tagged cubes at `collisionAnchor` and would be shown (visibility API is invoked).
- Move teleports only live volumes.
- Existing flag / PDC / rollback / tagged-remove tests still pass in mode A.

Live stand-on-top and six-face blocking remain the open ship-runtime acceptance item.

## Scope

Included: streamed cube pool, edge-distance index, hysteresis, player visibility, spawn-only non-player observers, per-ship A/B switch, inspect counts, tests.

Excluded: face-typed hitboxes, greedy face merges, non-cube collidable entities, Interaction entities as collision, duplicate per-player copies of the same cell, replacing Shulkers with the physics octree, persisting mode, `ShipConfig` keys, barrier blocks, changing carry.
