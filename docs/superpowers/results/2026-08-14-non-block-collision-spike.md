# Non-Block Collision Spike Result

## Status

**BLOCKED / UNVERIFIED — live player movement evidence is unavailable.**

## Build and server evidence

- Paper target: 26.2.
- `./gradlew spotlessApply test check` passed.
- The SpotBugs report has zero high- and medium-priority warnings.
- The collision manager and command constructors compile with their complete bodies restored.
- The built artifact is `build/libs/ships-0.1.0-SNAPSHOT.jar`.
- The managed Paper server restarted from the fresh build and reached `Done (...)!` readiness.
- Ships loaded and enabled successfully.
- `ShipsPlugin.registerCommand()` now installs the `ShipCommand` executor before the tab completer.
- `/ship collision-test` is registered in `plugin.yml`, dispatched by `ShipCommand`, and offered by tab completion.
- No player collision result was captured.

## Fixture commands

```text
/ship collision-test
/ship collision-test move 1
/ship collision-test move -1
/ship collision-test remove
```

The fixture uses an invisible, invulnerable, silent, gravity-free, no-AI, collidable Shulker with an ownership tag. It tracks each fixture's actual integer anchor and cleans fixtures during plugin shutdown.

## Missing evidence

A connected client must verify face collision from all six directions, water adjacency, integer movement, hitbox mismatch, interaction behavior, and cleanup. Compile/startup evidence cannot prove player/entity collision because player/entity collision is client-predicted.

## Decision

Do not integrate Shulker collision into production ships yet. The prototype is technically runnable, but the architecture decision remains unverified until a connected player records actual movement. If collision fails or the hitbox is unacceptable, reject Shulkers and use authoritative real blocks as the fallback.
