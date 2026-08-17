# Task 7 Report

## Event-driven tracker overlap regression

Added `BukkitShipRiderTrackerTest.eventOverlapUsesStoredSuppliedBasisInsteadOfMutableShipPose`.

Semantic setup: a one-block ship is tracked with supplied seed basis `4.0`; the mutable `Ship.pose()` is then changed to `20`. A real `io.papermc.paper.event.entity.EntityMoveEvent` is constructed with a LivingEntity proxy and delivered through package-private `BukkitShipRiderTracker.onEntityMove`. The entity's event destination is at y `5.01`, which overlaps the ship top only when the stored supplied basis `4.0` is used (and not when the later-mutated pose `20` is consulted). The assertion verifies the entity UUID is associated with the ship.

## Verification

Command:
```bash
./gradlew test --tests dev.mintychochip.ships.bukkit.BukkitShipRiderTrackerTest --tests dev.mintychochip.ships.bukkit.BukkitShipEntityCarrierTest --tests dev.mintychochip.ships.bukkit.TopSurfaceIndexTest --tests dev.mintychochip.ships.bukkit.BukkitCollisionVolumeManagerTest
```

Outcome: `BUILD SUCCESSFUL` (22 tests completed, 0 failed).

This is regression coverage for the existing stored-pose-basis implementation; no production code or test-only production hooks were changed.
