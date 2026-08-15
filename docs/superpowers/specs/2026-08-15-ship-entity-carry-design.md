# Ship Entity Carry Design

## Objective
When a ship moves vertically, carry non-ship entities that are standing on its exposed top hull blocks by the same vertical delta, like honey blocks.

## Scope
- Vertical movement only (matches existing `ShipRuntime.move(oldY, newY)`).
- Top surfaces of exposed hull blocks only.
- Exclude ship-owned Shulker collision volumes and BlockDisplay render volumes.
- Best-effort carry: entity teleport failures do not roll back the ship move.

## Approach
Introduce a `ShipEntityCarrier` interface and a Bukkit implementation `BukkitShipEntityCarrier`. The carrier is called by `ShipRuntimeImpl` only after `renderer.reposition` and `collisions.move` succeed. It snapshots candidate entities at the ship's **old** pose, then teleports them by `newY - oldY`.

## Integration point
- `ShipRuntimeImpl` gains an optional `ShipEntityCarrier` constructor parameter; the existing two-argument constructor delegates to a no-op carrier to preserve tests.
- `ShipsPlugin` constructs `BukkitShipEntityCarrier` with the Bukkit `World`, the collision owner `NamespacedKey`, and the render ship `NamespacedKey`, and passes it into `ShipRuntimeImpl`.
- `ShipTransform` gets an overload `visual(ship, relative, y)` so the carrier can compute old-pose surfaces without mutating the ship.
- `CollisionHull` gets a `topExposedBlocks(ship)` helper that returns blocks with no occupied +Y neighbor.

## Carry algorithm
1. `delta = newY - oldY`; return if zero.
2. Compute the list of top-exposed relative blocks using `CollisionHull.topExposedBlocks(ship)`.
3. For each top-exposed block, compute its old-pose top surface with `ShipTransform.visual(ship, pos, oldY).y() + 1.0`.
4. Build a single `BoundingBox` spanning all top-surface columns from `topY - 0.5` to `topY + 2.0`.
5. Build a 2D spatial grid keyed by integer block `x/z` so each top-surface column can be found by the cell(s) an entity's bounding box overlaps. A single entity only checks the 1-4 grid cells under its footprint instead of every top block.
6. Query `World.getNearbyEntities` once and post-filter: the entity must be valid, not dead, in the same world, not ship-owned, not a passenger, and its bounding box must overlap one of the top-surface columns.
7. Deduplicate by UUID and teleport each root entity by `(0, delta, 0)`, preserving yaw/pitch/world. A false teleport or exception is skipped.

## Optimization
- A `Map<Long, TopSurface>` keyed by packed block `x/z` replaces the O(N) per-entity linear scan with a footprint-only lookup. For a normal entity this is 1-4 cells, so large ships do not degrade the per-entity cost.
- Rejected alternatives:
  - Persistent event-driven rider `Set<UUID>`: requires Bukkit event listeners, lifecycle tracking, and staleness reconciliation for relatively small gain; a grid is stateless and safer.
  - AABB/BVH tree: overkill for the default `maximumBlocks` of 500 and adds balancing/allocation overhead; the grid provides the same tree-like pruning with lower constant cost.

## Transaction semantics
Carry is not part of the renderer/collision rollback. If renderer or collision fails, the existing rollback runs and no entities are teleported. Once both succeed, the carrier runs and any failures are swallowed to avoid partially rolling back a successful ship move.
