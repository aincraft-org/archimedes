
## Follow-up integrity repair

Restored the complete `ShipServiceImplTest` from parent commit `c0fbcdf`, then removed only the approved `NoopDeck` helper. This preserved the original null-scanner and render-rollback test coverage.

- Focused test: `./gradlew test --tests dev.mintychochip.ships.ship.ShipServiceImplTest --console=plain` — `BUILD SUCCESSFUL`.
- Full gate rerun: `./gradlew check --console=plain` — `BUILD SUCCESSFUL`.
