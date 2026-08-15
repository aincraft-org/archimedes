# Ship Entity Carry Implementation Plan

## Files

### Create
- `src/main/java/dev/jlo/ships/ship/ShipEntityCarrier.java`
- `src/main/java/dev/jlo/ships/ship/NoopShipEntityCarrier.java`
- `src/main/java/dev/jlo/ships/bukkit/BukkitShipEntityCarrier.java`

### Modify
- `src/main/java/dev/jlo/ships/model/ShipTransform.java` — add `visual(ship, relative, y)` overload.
- `src/main/java/dev/jlo/ships/collision/CollisionHull.java` — add `topExposedBlocks(ship)`.
- `src/main/java/dev/jlo/ships/ship/ShipRuntimeImpl.java` — inject carrier and call it.
- `src/main/java/dev/jlo/ships/ShipsPlugin.java` — wire `BukkitShipEntityCarrier`.
- `src/test/java/dev/jlo/ships/ship/ShipRuntimeImplTest.java` — add carrier call-order test.
- `src/test/java/dev/jlo/ships/collision/CollisionHullTest.java` — add top-exposed selection test.
- `src/test/java/dev/jlo/ships/bukkit/BukkitShipEntityCarrierTest.java` — compile-only test.

## Steps
1. Add `ShipTransform.visual` overload and `CollisionHull.topExposedBlocks`.
2. Add `ShipEntityCarrier` and `NoopShipEntityCarrier`.
3. Update `ShipRuntimeImpl` to accept and call the carrier.
4. Implement `BukkitShipEntityCarrier`.
5. Wire `BukkitShipEntityCarrier` in `ShipsPlugin`.
6. Add/update tests.
7. Run `./gradlew test`.
8. Run `./gradlew check`.
