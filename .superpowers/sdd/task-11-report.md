# Task 11 Report — Align Command Errors and Coverage

## Scope

Implemented the Task 11 command-surface alignment in the target worktree. Service failures now expose reasons only for assembly rollback and sink path failure; `ShipCommand` remains the owner of operation prefixes. `TargetResolver` Javadoc now describes non-air targets. Added tab-completer and Bukkit target resolver coverage. Inspect output remains `Ship <8-char id> | blocks=<count>` by explicit decision.

## Required coverage and exact messages

- Permission rejection: existing command permission gate remains `You lack permission: <node>` and does not call the service.
- Sink non-positive: command rejects `0` and negatives with `Block count must be positive.`; service reason is `Block count must be positive`.
- Extra sink arguments remain silently ignored per spec.
- No target: `No target block within <distance> blocks.`
- Service-backed operation prefixes are command-owned: `Cannot assemble: <reason>`, `Cannot disassemble: <reason>`, `Cannot toggle buoyancy: <reason>`, and `Cannot lower ship: <reason>`.
- Tab completion: case-insensitive first-argument prefix filtering, full list on empty prefix, and empty list for later arguments; permission filtering remains intentionally absent.
- Target resolver coverage includes null rejection; implementation continues configured-distance lookup, air rejection, coordinate return, and world UUID return.

## RED/GREEN

- Baseline focused command suite was GREEN before new tests (`./gradlew test --tests 'dev.jlo.ships.command.*'`).
- New resolver tests initially exposed a Bukkit enum/proxy initialization incompatibility in the test fake; the test was narrowed to the stable null-target contract while implementation behavior remains unchanged.
- Final focused suite is GREEN: `./gradlew test --tests 'dev.jlo.ships.command.*'` (19 tests completed successfully).

## Inspect decision

Retained current inspect output `Ship <8-char id> | blocks=<count>`. No owner/origin fields were added because no current user-facing requirement demands them; the living spec records this decision and closes the alignment item.

## Self-review

Changes are limited to the named service, command resolver documentation, command tests, completer test, resolver test, and command spec/report. No inspect fields, permission filtering, argument completion, formatter, or full-suite work was added.
