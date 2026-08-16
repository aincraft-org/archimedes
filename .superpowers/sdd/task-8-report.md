
## Final boundary assertion fix
The negative-floor movement test now captures each Shulker with its own teleport list. It asserts both lists remain empty for `-1.75 -> -1.25`, then each contains exactly one teleport after crossing to `-0.75`, with exact destinations `(0.5,-0.75,0.5)` and `(1.5,-0.75,0.5)`.

Final focused command:
```bash
./gradlew test --tests dev.jlo.ships.bukkit.BukkitCollisionVolumeManagerTest
```
Outcome: `BUILD SUCCESSFUL`; focused test file passed with 9 tests and 0 failures.
