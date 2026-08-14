# Ship Building Plugin Design

## Goal

Provide a Paper plugin that lets players convert an ordinary connected Minecraft build into a persistent, stationary ship rendered with block displays. Players can walk on the ship's exposed upper surfaces. Propulsion, steering, engines, turbines, rotation, and moving collision are deferred.

## Platform

The repository uses the `../skills/repository-setup` Paper starter conventions:

- Paper 26.2
- Java 25 toolchain
- Gradle Kotlin DSL and wrapper
- Paperweight Userdev
- JPenilla `runServer`
- Spotless, Checkstyle, PMD, and SpotBugs build gates

The plugin is named `Ships`, uses package `dev.jlo.ships`, and stores generated development-server state under the ignored `run/` directory.

## Player Workflow

1. A player builds a block-grid ship from ordinary blocks.
2. The player looks at any block belonging to the intended ship and runs `/ship assemble`.
3. The plugin performs a six-directional flood fill through face-connected, allowed non-air blocks.
4. Assembly stops with no world changes if the component exceeds the configurable maximum block count or contains a forbidden block.
5. The plugin records each block's material and block data relative to a stable ship origin.
6. The source blocks are removed and replaced by block-display entities grouped as one ship.
7. Collision entities cover exposed top faces, allowing players to stand and walk on the stationary ship.
8. Looking at a ship and running `/ship inspect` reports its identifier, owner, block count, and origin.
9. Looking at a ship and running `/ship disassemble` validates every destination first, removes ship entities, and restores the original blocks. If any destination is occupied, disassembly fails without partial changes.

## Commands and Permissions

- `/ship assemble` — `ships.assemble`
- `/ship inspect` — `ships.inspect`
- `/ship disassemble` — `ships.disassemble`

Only the owner or an operator may disassemble a ship. Commands require a player because targeting and ownership are player-scoped. Errors are explicit for no target, oversized components, forbidden blocks, occupied restoration space, and missing ownership.

## Architecture

### Assembly scanner

`ShipScanner` performs a bounded breadth-first traversal from the targeted block. It visits six face-adjacent positions, rejects forbidden materials, and aborts as soon as the configured size limit is exceeded. Scanning is read-only; no block changes happen until the entire candidate is valid.

### Ship model and persistence

A `Ship` contains a UUID, owner UUID, world UUID, integer origin, and immutable relative block snapshots. Each snapshot stores relative coordinates plus the exact `BlockData` string needed for restoration and display creation.

`ShipStore` persists ships in the plugin data directory. Writes use a temporary file followed by replacement so interrupted saves do not leave a partially written primary file. Persistence contains model state rather than entity runtime identifiers; entities are recreated deterministically during reconciliation.

### Entity rendering

`ShipRenderer` creates one block display for each stored block using the saved block data and relative transform. Each entity carries the ship UUID in its persistent data container. Displays are non-persistent at the Bukkit entity level so the plugin remains the single persistence authority.

### Walkable surfaces

`ShipCollision` identifies stored blocks with no ship block directly above them. Paper interaction entities are hit-test targets, not solid floors, so they cannot satisfy the walkability requirement. The stationary MVP therefore places a temporary barrier support block one block above each exposed-top ship block, at source position plus `(0, 1, 0)`. Every support destination must be air and must not overlap another stored ship block; assembly fails before mutation if any destination is obstructed. Support positions are derived from the persisted ship model and removed before source blocks are restored, during plugin disable, and when stale runtime state is reconciled.

Plugin enable performs a runtime capability check before loading ships: every configured support material must be a solid, non-gravity block and the world mutation API must be available. A failed check disables the plugin with a clear error. Visual-only decks are prohibited.

### Registry and reconciliation

`ShipRegistry` owns loaded ships and runtime entity groups. On enable it loads persisted state, removes tagged orphan entities, and recreates each registered ship's displays and collision. On disable it removes runtime entities while retaining persisted model records.

### Commands

`ShipCommand` handles command parsing, targeting, permissions, ownership checks, and user-facing messages. Domain services perform scanning, transactional assembly, inspection lookup, and disassembly so command code does not own state transitions.

## State Transitions and Failure Safety

Assembly follows validate, snapshot, persist, mutate, render. If persistence fails, the world remains unchanged. If mutation or rendering fails, the service restores removed blocks, removes partial entities, and removes the uncommitted ship record.

Disassembly follows target, authorize, validate destinations, restore blocks, remove entities, remove record. Validation precedes every mutation. A runtime failure attempts to return to the assembled state and reports the failure without deleting the persistent record.

Chunk boundaries are allowed only when all required chunks are already loaded. The plugin does not synchronously generate or load arbitrary chunks during traversal.

## Configuration

`config.yml` contains:

- `maximum-blocks`: bounded positive assembly limit
- `target-distance`: maximum command targeting distance
- `forbidden-materials`: materials unsafe or inappropriate to capture, including air variants, portals, command blocks, structure blocks, jigsaws, moving pistons, and technical blocks

Invalid configuration fails plugin enable with a clear logged error rather than selecting unsafe defaults.

## Verification

Unit tests cover bounded six-directional traversal, relative snapshot generation, exposed-top detection, ownership authorization, destination occupancy validation, and persistence round trips. Service tests use controlled Bukkit fixtures where supported.

Runtime verification uses `./gradlew runServer`: build a small connected hull, assemble it, confirm source blocks become displays, walk across every exposed top face, restart the server and confirm reconstruction, inspect it, then disassemble and compare restored block data. The build must also pass the starter's full `check` lifecycle.

## Explicitly Deferred

- Ship movement, steering, acceleration, rotation, and buoyancy
- Engines, turbines, fuel, power networks, or propulsion
- Seats, passengers, weapons, damage, docking, or ship-to-ship merging
- Freeform display transforms and decorative non-grid parts
- Cross-world travel and unloaded-chunk traversal
