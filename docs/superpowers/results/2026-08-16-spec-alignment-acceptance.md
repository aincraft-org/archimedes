# Spec Alignment Live Acceptance — 2026-08-16

## Build under test

- Plugin commit: `85a325b` plus Task 8 follow-up commits through the current branch head.
- Requested server: Paper 26.2.
- Resolved server: Paper 26.2 build 112, commit `c9e894d`, API `26.2.build.112-stable`.
- Launch command: `./gradlew runServer` from the `spec-alignment` worktree.
- Relevant configuration: generated disposable `run/` configuration; EULA accepted locally for the disposable run.

## Observed startup evidence

Paper resolved the plugin as `Ships 0.1.0-SNAPSHOT` and reached network bind initialization. The disposable instance could not bind `*:25565` because an existing persistent Paper process owned the port. No plugin initialization failure occurred before the bind conflict.

## Live fixture and geometry matrix

Status: **BLOCKED — not observed**.

A connected Minecraft client and an available Paper port are required to construct and physically traverse the fixed fixture. This session had neither a client connection nor permission to terminate the existing persistent server. Therefore no claim is made for:

- standing on exposed top faces;
- north, south, east, and west face blocking;
- underside blocking;
- sprint/jump pass-through resistance;
- absence of placed barrier/deck blocks in a live world;
- visual alignment during fractional and integer-anchor movement;
- restart reconstruction and stale-entity absence;
- disassembly cleanup.

## Reproduction procedure

1. Stop or move the existing server currently bound to TCP 25565, or configure the disposable server to an unused port.
2. Launch `./gradlew runServer` from the exact plugin commit under test.
3. Connect a Minecraft 26.2 client.
4. Build a small exposed-block ship whose top, bottom, north, south, east, and west faces are reachable; record world UUID, origin coordinates, dimensions, and config values.
5. Assemble it and record each six-direction collision observation, including sprint/jump attempts and whether any barrier/deck blocks were placed.
6. Observe a fractional bob that stays within one integer collision anchor, then a bob crossing an integer anchor; record model/hull alignment.
7. Restart the server, verify reconstruction and absence of stale runtime entities, then disassemble and verify cleanup.

## Decision

Live Shulker geometry acceptance remains open. Automated manager tests verify configured Shulker state, canonical anchors, identity tags, guarded movement, and rollback, but those results are not substituted for live player-physics evidence.
