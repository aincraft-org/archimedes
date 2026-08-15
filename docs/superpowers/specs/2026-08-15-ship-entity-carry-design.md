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
- `ShipsPlugin` constructs `BukkitShipRiderTracker` and `BukkitShipEntityCarrier`, registers the tracker as a Bukkit event listener, and passes the carrier into `ShipRuntimeImpl`.
- `ShipTransform` gets an overload `visual(ship, relative, y)` so the carrier can compute old-pose surfaces without mutating the ship.
- `CollisionHull` gets a `topExposedBlocks(ship)` helper that returns blocks with no occupied +Y neighbor.

## Carry algorithm
1. `delta = newY - oldY`; return if zero.
2. `BukkitShipRiderTracker` maintains a per-ship `Set<UUID>` of on-board entities. It builds a 2D spatial grid keyed by integer block `x/z` once per ship, then updates the rider sets from Bukkit entity events (move, spawn, death, quit, world change, teleport, vehicle enter/exit).
3. `BukkitShipEntityCarrier` calls `tracker.track(ship)` once to seed the set with any entities already on the ship, then retrieves the known riders and teleports each root entity by `(0, delta, 0)`, preserving yaw/pitch/world.
4. Post-filter: the entity must be valid, not dead, in the same world, not ship-owned, and not a passenger. A false teleport or exception is skipped.

## Optimization
- A 2D spatial grid keyed by packed block `x/z` reduces the per-entity overlap test to the 1-4 cells under the entity's footprint.
- A persistent `Set<UUID>` of on-board entities is maintained by a Bukkit event listener, so a vertical ship move only needs to look up and teleport the already-known riders instead of calling `World.getNearbyEntities` on every move.
- The grid is stored pose-invariant (at y = 0) and queried with the current ship pose y, so the index is built once per ship and reused across moves.
- `BukkitShipRiderTracker` reacts to movement, spawn, vehicle enter/exit, death, quit, teleport, and world-change events to keep the rider set accurate.
