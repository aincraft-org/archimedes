# Ship Runtime Transform and Collision Design

## Problem

Production ships currently split one logical ship across incompatible coordinate and lifecycle systems:

- `DeckManager` writes barrier blocks for collision and walkability.
- Block displays use implicit half-block offsets and are repositioned from mutable entity locations.
- The Shulker collision prototype is debug-only and never belongs to an assembled ship.
- Buoyancy validates movement before removing ship-owned barriers, allowing supports to block the next move.
- Persisted ships load as models without reconstructing their runtime displays or collision hulls.

The result is visible origin drift, barrier-backed collision, downward movement that can stop after one block, and collision entities that are unrelated to assembled ships.

## Goals

- Remove barrier blocks from production ship collision.
- Align every BlockDisplay to its canonical block corner.
- Attach entity collision volumes to each assembled ship.
- Move the model, displays, and collision hull through one transaction.
- Allow unobstructed multi-block sinking.
- Reconstruct runtime displays and collision volumes from persisted ships.
- Preserve exact source block data and existing disassembly behavior.

## Non-goals

- Horizontal navigation or ship rotation.
- Smooth collision interpolation between integer vertical anchors.
- Perfect rectangular collision for every Minecraft block shape.
- Supporting multiple Bukkit worlds in this change.

## Canonical transform

A ship position has exactly three inputs:

1. `ShipOrigin`: the absolute integer build origin.
2. `ShipBlock.pos`: the block's integer position relative to the origin.
3. `ShipPose.y`: the ship's fractional vertical displacement.

The transform exposes two projections:

- Visual block corner: `(origin.x + rel.x, origin.y + pose.y + rel.y, origin.z + rel.z)`.
- Authoritative cell: `(origin.x + rel.x, origin.y + floor(pose.y) + rel.y, origin.z + rel.z)`.

BlockDisplays use visual block-corner positions without an implicit `+0.5`. Collision entities, clearance checks, world restoration, and persistence reconciliation use authoritative cells. No consumer duplicates this arithmetic.

## Rendering

`BukkitShipRenderer` renders one tagged BlockDisplay per `ShipBlock`. Runtime identity includes the ship identifier and stable relative block position.

Initial render and reposition both derive locations from the canonical model. Reposition never reverse-engineers relative coordinates from an entity's current location. This makes movement idempotent and prevents cumulative drift.

## Entity collision hull

Production ships use invisible Shulkers as entity collision volumes. Barrier blocks are not deployed.

The collision manager owns a collection of volumes per ship, keyed by stable relative hull position. For the first implementation, the hull contains one collision entity for each exposed solid ship block. Each entity is:

- invisible;
- invulnerable;
- silent;
- gravity-free;
- AI-free;
- collidable;
- non-persistent at the Bukkit entity level;
- tagged with the ship identifier and relative block key.

Volumes spawn at authoritative cells using the collision adapter's explicitly defined entity anchor. They move only when the authoritative pose anchor changes. Fractional bobbing moves displays but leaves collision at the current integer anchor, matching world clearance and restoration semantics.

Because Shulker hitboxes are not exact block geometry, live walkability is an acceptance requirement. The entity-only direction is intentional; no barrier fallback remains in production code.

## Lifecycle

### Assembly

1. Scan and snapshot the connected component.
2. Clear source blocks.
3. Spawn the collision hull.
4. Render displays.
5. Register and persist the ship.
6. Apply initial buoyancy through the normal movement transaction.

Any failure removes collision and display entities, restores source blocks, unregisters the model, clears buoyancy state, and persists the rollback.

### Movement

1. Compute source and target authoritative anchors.
2. Validate all traversed target cells against world blocks. Entity collision owned by the moving ship is not a world-block obstruction.
3. Update the pose.
4. Recompute and teleport displays from the model.
5. If the authoritative anchor changed, move collision volumes from the model.
6. If any runtime update fails, restore the old pose, display positions, and collision positions.
7. Persist only after success.

No deck support removal or redeployment occurs, eliminating self-obstruction during multi-block sinking.

### Disassembly

Validate world restoration against authoritative cells, restore exact block snapshots, remove displays and collision volumes, clear buoyancy state, remove the model, and persist.

### Disable and restart

Disable removes non-persistent runtime displays and collision volumes without deleting persisted models.

Startup loads persisted models, then reconciles runtime state for each ship: remove stale tagged entities, spawn the expected collision hull, and render expected displays. A reconstruction failure must not silently leave a model-only ship; plugin startup fails with a precise error after cleaning partial runtime entities.

## Walkability semantics

Deck barriers currently provide a flat support plane above exposed top blocks. Entity-only collision must preserve the user-observable contract: a player can stand on exposed horizontal ship surfaces and is blocked by hull sides.

Hull selection therefore includes exposed top-bearing blocks and outer surface blocks. Internal blocks do not need duplicate collision entities. The exact exposed-hull calculation is deterministic from relative block positions and is covered by unit tests. Live Paper verification checks continuity and edge behavior; if Shulker geometry cannot meet this contract, the entity architecture is rejected rather than quietly restoring barriers.

## Error handling

- Collision spawn is all-or-nothing per ship.
- Runtime entity operations are idempotent by ship and relative block key.
- Partial assembly and startup reconciliation clean every entity already spawned.
- Movement reports path blockage separately from runtime teleport failure.
- Persistence occurs after complete runtime success or complete rollback.

## Tests

Focused tests cover:

- visual and authoritative projections for positive, fractional, and negative poses;
- BlockDisplay origin alignment;
- repeated reposition without X/Z drift;
- deterministic exposed hull selection;
- collision spawn, move, remove, and rollback lifecycle;
- assembly without barrier placement;
- multi-block and repeated sinking through water/air;
- blocked movement rollback;
- persisted runtime reconstruction;
- disassembly and disable cleanup.

Live Paper acceptance covers:

- no barrier blocks after assembly;
- visual blocks aligned with their original integer cells;
- collision entities spawn with the assembled ship, not the player fixture;
- walking on exposed deck surfaces and collision from hull sides;
- sinking by more than one block and repeated sink commands;
- visual/collision alignment after movement;
- restart reconstruction and disassembly cleanup.
