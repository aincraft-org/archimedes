
## Review follow-up

Review found the sink service-failure branch did not send a message. Fixed `ShipCommand` to emit exactly one `Cannot lower ship: <reason>` prefix for `service.sink(...) == false`; added a direct regression test. Updated the three `ShipServiceImplTest` rollback assertions to the reason-only contract (`persist`, `persist failed`, `spawn failed`). Verification: `./gradlew test --tests 'dev.jlo.ships.command.*' --tests 'dev.jlo.ships.ship.ShipServiceImplTest'` passed (42 tests).
