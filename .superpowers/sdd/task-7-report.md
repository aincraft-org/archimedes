# Task 7 Report

## RED
Command:
```bash
./gradlew test --tests dev.jlo.ships.ship.ShipRuntimeImplTest.upwardCollisionFailureRestoresBasisAfterReverseCarry
```
Outcome: failed as expected with an assertion failure at `ShipRuntimeImplTest.java:83`; the recording carrier modeled Bukkit semantics by storing the first `carry` argument as its basis, exposing that rollback ended at `newY` before the production ordering fix.

## GREEN
Command:
```bash
./gradlew test --tests dev.jlo.ships.bukkit.BukkitShipEntityCarrierTest --tests dev.jlo.ships.ship.ShipRuntimeImplTest
```
Outcome: `BUILD SUCCESSFUL`; both focused Task 7 test classes passed.

## Changes
- Added direct upward rollback coverage with a recording carrier whose `carry` records its oldY argument as the pose basis.
- Reordered rollback so reverse carrying completes before restoring the old pose basis, ensuring rollback ends at `oldY`.

## Changed files
- `src/main/java/dev/jlo/ships/ship/ShipRuntimeImpl.java`
- `src/test/java/dev/jlo/ships/ship/ShipRuntimeImplTest.java`

## Self-review
- Reviewed the focused diff: production change is limited to rollback ordering; the regression asserts the externally observable final basis.
- No formatter, linter, project-wide build, or project-wide test suite was run.

## Concerns
- The requested seed-pose and tracker event-overlap coverage already exists in the current focused Bukkit carrier/tracker implementation and its existing focused tests; this fix did not alter those files.
