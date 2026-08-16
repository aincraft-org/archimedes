
## Follow-up review fixes
Strengthened the focused test coverage after review rejection:
- `spawnConfiguresTaggedInvisibleInvulnerableNonPersistentCollisionAtCanonicalAnchor` now captures and asserts every required Shulker setter argument (`AI`, invisibility, invulnerability, silence, gravity, collidability, peek, persistence), both PDC values, scoreboard tag, and exact canonical spawn location.
- Added multi-volume movement coverage using fractional negative floors: unchanged authoritative floor produces zero teleports, while crossing the boundary teleports both volumes exactly once.
- Added multi-volume rollback coverage asserting both volumes return into the old-anchor range.

Follow-up GREEN command:
```bash
./gradlew test --tests dev.jlo.ships.bukkit.BukkitCollisionVolumeManagerTest
```
Outcome: `BUILD SUCCESSFUL`; 9 tests completed, 0 failed.
