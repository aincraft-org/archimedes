# Task 12 Report

## Scope

Removed the dead deck production and test implementation and corrected current runtime wording. The historical barrier-backed deck decision remains preserved in the historical design/result records.

## Changes

- Deleted `DeckManager`, `DeckSurface`, and `BukkitDeckSurface` production sources.
- Deleted `DeckManagerTest`, `DeckSurfaceTest`, and `DeckSurfaceTestHelper`.
- Removed `NoopDeck` from `ShipServiceImplTest`.
- Updated `ShipService.removeAllRuntime` Javadoc to describe runtime entities only.
- Updated plugin metadata to describe persistent display-and-collision ships without promising walkable decks.
- Removed the dead-deck known-debt entry and marked runtime cleanup complete.
- Updated the runtime spec to record the Shulker-hull collision path and preserve the historical decision context.

## Verification

- `./gradlew check --console=plain`: `BUILD SUCCESSFUL` (162 tests completed).
- Source scan for `DeckManager|DeckSurface|BukkitDeckSurface|NoopDeck` under `src`: no matches.
- Current specs scan for stale `walkable decks`, `entities and barriers`, and the dead-package cleanup wording: no stale matches; historical design records remain unchanged.
