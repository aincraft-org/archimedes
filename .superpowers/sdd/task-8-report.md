
## Second review coverage fixes
Added exact negative-floor progression coverage: pose `-1.75 -> -1.25` remains authoritative floor `-2` with zero teleports, then `-1.25 -> -0.75` crosses to floor `-1` and teleports both volumes exactly once at `y=-0.75`. Strengthened rollback to assert exact old anchors `(0.5,0.25,0.5)` and `(1.5,0.25,0.5)` for the two volumes.

Final focused command:
```bash
./gradlew test --tests dev.jlo.ships.bukkit.BukkitCollisionVolumeManagerTest
```
Outcome: `BUILD SUCCESSFUL`; focused test file passed with 9 tests and 0 failures.
