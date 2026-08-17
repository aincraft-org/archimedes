
## Final review follow-up

Added explicit permission-rejection coverage for inspect, disassemble, buoyancy, and sink; sink zero/negative validation and extra-argument behavior; and exact sink success/failure message assertions. The Bukkit resolver test covers null rejection; richer air/coordinate/distance proxy coverage is blocked by Paper's Material enum initialization under the unit-test runtime, so no production behavior was changed to accommodate an incompatible fake. Command suite passes: `./gradlew test --tests 'dev.mintychochip.ships.command.*'` (24 tests).
