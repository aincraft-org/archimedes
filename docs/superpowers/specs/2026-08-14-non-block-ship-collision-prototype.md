# Non-Block Ship Collision Prototype

## Goal

Determine whether an assembled ship can retain `BlockDisplay` visuals and provide player-solid collision without placing `Material.BARRIER` or captured hull blocks into the world.

This is an API/runtime spike, not a production ship-body implementation.

## Verified findings

- The plugin renders one `BlockDisplay` per captured ship block.
- `BlockDisplay` is visual and does not provide ordinary solid block collision for player movement.
- `DeckManager` places barriers only above exposed ship tops. Those barriers provide the current walkable collision support; they are not a hull body.
- The project targets Paper `26.2` through `paperDevBundle("26.2.build.+")` and `paper-api:26.2.build.+`.
- Paper 26.2 exposes entity collision state and bounding boxes.
- Shulker is the chosen prototype entity because it is collision-capable and can be invisible, invulnerable, silent, gravity-free, no-AI, and kept closed with `peek = 0`.
- Marker armor stands are rejected for this spike because marker mode deliberately has a tiny hitbox.
- Interaction entities are rejected because they detect interaction but do not physically block players.
- Paper documents client-predicted player/entity collision; API flags alone are insufficient evidence. A live player movement test is mandatory.

## Chosen prototype

Use invisible, invulnerable, no-AI Shulkers as temporary collision volumes. Spawn one Shulker per simple test volume, tag it with the ship UUID, and move it with the ship integer anchor. The prototype must first prove a one-block volume, then measure a rectangular multi-block volume. It must not assume that a Shulker's hitbox exactly matches arbitrary ship geometry.

The implementation must compile against the configured Paper 26.2 dependency and use the actual available method signature for `setPeek`. No API signature is assumed by this specification.

Required runtime configuration:

- invisible
- invulnerable
- silent
- gravity disabled
- AI disabled
- collidable enabled
- closed peek state
- ownership tag containing the ship UUID

## Scope boundary

- Add a collision-volume abstraction separate from rendering and deck supports.
- Add a Bukkit Shulker-backed prototype implementation.
- Spawn, move, and remove only prototype collision entities.
- Keep the current production assembly and buoyancy path unchanged until the spike passes.
- Do not place barriers for hull collision. Existing deck barriers remain a separate feature during the spike.
- Do not implement horizontal movement, fractional collision, passenger handling, or production persistence in this spike.

## Exact runtime test cases

Run on the managed Paper server using a temporary/debug test hook or command fixture:

1. **Single-volume block:** Spawn one visual block display and one invisible Shulker at an empty location. Walk a player into the volume from north, south, east, west, above, and below. The player must not pass through.
2. **Water adjacency:** Place water immediately beside the volume. Approach from the water side and from air. The collision must remain present and the water must not be replaced.
3. **Anchor movement:** Move the volume one integer block vertically. Confirm collision at the new location and no collision at the old location.
4. **Rectangular volume:** Spawn the smallest multi-block test hull used by the plugin. Compare the Shulker hitbox against the intended hull footprint and record any over-blocking or gaps.
5. **Normal interaction:** Confirm the Shulker is invisible, silent, does not attack, does not fall, and cannot be damaged. Cancel interaction/damage events if the runtime test exposes them.
6. **Cleanup:** Remove the test volume and confirm the player can pass through the old location. Repeat after plugin disable/re-enable and confirm no tagged collision entity remains.

## Automated tests

Unit tests use a fake collision entity/surface and verify:

- All required entity flags are applied.
- The ship UUID ownership tag is applied.
- Movement updates the integer anchor.
- Cleanup removes only entities tagged for that ship.
- No barrier placement is performed by the collision-volume implementation.

These tests do not replace the live player movement test because Bukkit unit tests cannot reproduce client-predicted collision.

## Acceptance criteria

The spike passes only if:

- The player is blocked from every tested face of the single-volume Shulker.
- Water remains unchanged.
- The volume follows integer-anchor movement with no stale entity at the old location.
- The entity is invisible and non-interactive during normal gameplay.
- Cleanup is complete on explicit removal, rollback, disable, and restart reconciliation.
- The rectangular hitbox mismatch is acceptable for the intended ship hull.
- No hull collision barriers are placed.

Any player pass-through, unacceptable hitbox mismatch, leaked entity, unwanted interaction, or water mutation fails the spike.

## Production fallback

If the Shulker spike fails, reject non-block collision for production. Use an authoritative real-block body instead: move exact captured block data at integer anchors with destination validation, water allowance, ownership tracking, and atomic rollback. Do not expand the current display-plus-barrier proxy after a failed spike.
