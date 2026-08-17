# Specification Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the repository’s implemented behavior, safety guarantees, automated evidence, live acceptance evidence, and authoritative living specs agree.

**Architecture:** Use a risk-first staged cutover. First make the living specs truthful, then enforce configuration/world policy, harden lifecycle transactions, add behavioral proof, remove dead production code, and only afterward design the larger buoyancy model. Every behavior change lands with its regression test and the affected living-spec checkbox or decision update.

**Tech Stack:** Java 25, Paper 26.2, Gradle Kotlin DSL, JUnit Jupiter 5.13.4, Gson, Spotless, Checkstyle, PMD, SpotBugs.

## Global Constraints

- `docs/specs/` is authoritative; `docs/superpowers/specs/`, `plans/`, and `results/` are historical records.
- Domain code must not import Bukkit; Bukkit adapters stay under `dev.mintychochip.ships.bukkit`.
- `ShipTransform` remains the canonical visual, authoritative-cell, and collision-anchor projection.
- `ships.json` remains the single persistence authority; runtime entities remain non-persistent.
- Use clean cutovers: migrate all callers and remove obsolete APIs, helpers, comments, and tests in the same task.
- Each permanent behavior change starts with a failing observable-contract test.
- Run focused tests after each task and `./gradlew check` at each stage gate.
- Live Paper checks are required where mocks cannot establish Bukkit collision geometry or entity behavior.

---

## Audit Baseline

The current `./gradlew check` succeeds. The audit found these material gaps:

1. `disabled-worlds` is parsed by `ShipConfigLoader` and exposed by `ShipConfig`, but `ShipsPlugin` never consults it.
2. Runtime is bound to `Bukkit.getWorlds().get(0)` while commands resolve the player’s current target world; the living specs do not consistently state this primary-world-only restriction.
3. `ShipRuntimeImpl` and `ShipServiceImpl` roll back only `ShipRuntimeException`; unchecked adapter/entity failures can leave world blocks, displays, hulls, or model state partially mutated.
4. `ShipServiceImpl.loadAll()` performs the first stale-entity sweep outside its cleanup boundary, and plugin disable cleanup is unguarded.
5. Rider tracking has no explicit ship removal/disable lifecycle cleanup and has an old-pose seed/current-pose update window.
6. Production Shulker collision is not live-accepted; `BukkitCollisionVolumeManagerTest` is compile-only.
7. Buoyancy intent promises force-balance equilibrium and rider-load response, while Current behavior is geometry-based, uniform-density, and rider-mass-free.
8. `ScanResult.captured()` exposes a mutable list.
9. Sink failures can render `Cannot lower ship: Cannot lower ship: path blocked` because both service and command add the operation prefix.
10. The dead `deck/` package and stale deck/barrier wording remain.

---

## Stage 1 — Truthful Contracts

### Task 1: Reconcile Current Behavior and Spec Claims

**Files:**
- Modify: `docs/specs/README.md`
- Modify: `docs/specs/ship-model.md`
- Modify: `docs/specs/ship-runtime.md`
- Modify: `docs/specs/buoyancy.md`
- Modify: `docs/specs/commands.md`

**Interfaces:**
- Consumes: Current audited implementation behavior.
- Produces: Unambiguous contracts used by every later task.

- [ ] **Step 1: Correct the world-scope contract**

State explicitly that the current runtime supports only the primary Bukkit world, that command resolution may identify another world but assembly is rejected there, and that `disabled-worlds` must also reject the bound world once Task 3 lands. Keep cross-world runtime support in Future.

- [ ] **Step 2: Correct buoyancy intent**

Separate Current from target behavior:

```text
Current: geometry-based waterline equilibrium, uniform block density, no rider mass.
Next: aggregate per-material mass plus rider load, with equilibrium solved from displaced water.
```

Remove present-tense claims that boarding already changes draft or that equilibrium already solves force balance.

- [ ] **Step 3: Record the runtime failure target**

Keep the Current invariant truthful: rollback is scoped to `ShipRuntimeException`, so unchecked adapter/entity failures may bypass cleanup. Add the desired guarantee under Next: adapter/runtime failures are normalized to `ShipRuntimeException`; spawn, move, and reconciliation either complete or restore their pre-operation model/runtime/world state. Explicitly exclude best-effort rider transport from ship rollback. Tasks 4–6 promote this target into the invariant and Current only after their regression tests pass.

- [ ] **Step 4: Define command error ownership**

Specify that `ShipService.lastError()` contains a reason without the command’s operation prefix. Commands own `Cannot assemble:`, `Cannot disassemble:`, `Cannot toggle buoyancy:`, and `Cannot lower ship:`.

- [ ] **Step 5: Record decisions and update dates**

Add decision-log rows for primary-world scope and command error ownership. Record failure normalization as a Next target rather than an implemented decision; Tasks 4–6 add the final decision only after verification. Do not check implementation boxes until their tasks pass.

- [ ] **Step 6: Review spec consistency**

Verify every Current statement describes current code, every future behavior appears only under Next/Future, and no dated design document is treated as authority.

- [ ] **Step 7: Commit**

```bash
git add docs/specs
git commit -m "docs: reconcile living specs with current behavior"
```

**Stage gate:** A reader can derive current behavior and intended alignment work without reading dated documents.

---

## Stage 2 — Configuration and World Policy

### Task 2: Make Scan Results Immutable

**Files:**
- Modify: `src/main/java/dev/jlo/ships/scan/ScanResult.java`
- Modify: `src/test/java/dev/jlo/ships/scan/ShipScannerTest.java`
- Modify: `docs/specs/ship-model.md`

**Interfaces:**
- Consumes: `ScanResult.captured(): List<BlockPos>`.
- Produces: The same accessor signature backed by an immutable defensive copy.

- [ ] **Step 1: Add the failing immutability test**

Add a test that obtains `captured()` from a successful scan, asserts `captured().add(...)` throws `UnsupportedOperationException`, mutates the source list used to create a result if the constructor/factory permits it, and verifies the result is unchanged.

- [ ] **Step 2: Run the focused test and confirm failure**

```bash
./gradlew test --tests dev.mintychochip.ships.scan.ShipScannerTest
```

Expected: the new mutation assertion fails against the current caller-owned list.

- [ ] **Step 3: Defensively copy once**

Store `List.copyOf(captured)` when constructing a successful `ScanResult`. Do not allocate a new copy on every accessor call.

- [ ] **Step 4: Re-run the focused test**

```bash
./gradlew test --tests dev.mintychochip.ships.scan.ShipScannerTest
```

Expected: PASS.

- [ ] **Step 5: Update the living spec**

Check “Scan result defensively copied,” remove the mutable-list Current note, and update `Last updated`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/jlo/ships/scan/ScanResult.java src/test/java/dev/jlo/ships/scan/ShipScannerTest.java docs/specs/ship-model.md
git commit -m "fix: make scan results immutable"
```

### Task 3: Enforce Disabled and Primary-World Policy

**Files:**
- Modify: `src/main/java/dev/jlo/ships/ShipsPlugin.java`
- Modify: `src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java`
- Modify: `src/test/java/dev/jlo/ships/config/ShipConfigLoaderTest.java`
- Modify: `docs/specs/ship-model.md`
- Modify: `docs/specs/commands.md`

**Interfaces:**
- Consumes: `ShipConfig.worldEnabled(UUID)` and the primary world chosen by `WorldBinding`.
- Produces: One enforced policy: assembly is allowed only in the bound primary world and only when that UUID is enabled.

- [ ] **Step 1: Add service contract tests**

Add focused tests for:

```text
bound enabled world -> scanner is called
non-bound world -> null, scanner not called, lastError is the world-policy reason
bound disabled world -> null, scanner not called, lastError is the disabled-world reason
```

Extend the service constructor with the smallest explicit policy input: either `boolean worldEnabled` or a predicate keyed by UUID. Prefer `boolean worldEnabled` because the service is single-world and no multi-world abstraction exists.

- [ ] **Step 2: Confirm the tests fail**

```bash
./gradlew test --tests dev.mintychochip.ships.ship.ShipServiceImplTest
```

Expected: disabled-bound-world behavior is absent.

- [ ] **Step 3: Wire configuration into the service**

In `ShipsPlugin`, calculate `config.worldEnabled(world.getUID())` and pass it to `ShipServiceImpl`. In `assembleAt`, reject a disabled bound world before scanning or mutating.

- [ ] **Step 4: Expand config normalization coverage**

Add `ShipConfigLoaderTest` cases for lowercased forbidden materials, blank forbidden entries dropped, missing keys using documented defaults, and a representative invalid finite/range value rejecting load.

- [ ] **Step 5: Run focused tests**

```bash
./gradlew test --tests dev.mintychochip.ships.ship.ShipServiceImplTest --tests dev.mintychochip.ships.config.ShipConfigLoaderTest
```

Expected: PASS.

- [ ] **Step 6: Update living specs**

Document the exact rejection order and messages. Check disabled-world enforcement only after the focused tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/jlo/ships/ShipsPlugin.java src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java src/test/java/dev/jlo/ships/config/ShipConfigLoaderTest.java docs/specs/ship-model.md docs/specs/commands.md
git commit -m "fix: enforce configured ship world policy"
```

**Stage gate:** Run `./gradlew check`; expected `BUILD SUCCESSFUL`.

---

## Stage 3 — Transaction and Lifecycle Safety

### Task 4: Normalize Bukkit Adapter Failures

**Files:**
- Modify: `src/main/java/dev/jlo/ships/bukkit/BukkitShipRenderer.java`
- Modify: `src/main/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManager.java`
- Modify: `src/main/java/dev/jlo/ships/bukkit/BukkitShipEntityCarrier.java`
- Modify: `src/test/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManagerTest.java`
- Modify: `src/test/java/dev/jlo/ships/bukkit/BukkitShipEntityCarrierTest.java`
- Modify: `docs/specs/ship-runtime.md`

**Interfaces:**
- Consumes: Paper entity spawn, teleport, remove, PDC, and scoreboard APIs.
- Produces: Runtime-critical adapter failures surface as `ShipRuntimeException`; best-effort rider transport remains non-throwing by contract.

- [ ] **Step 1: Enumerate runtime-critical calls**

For renderer and collision manager, classify spawn, tagging, teleport, pairing, and removal calls as transaction-critical. Keep rider player velocity and non-player teleport failures best-effort as documented.

- [ ] **Step 2: Add failing adapter tests**

Use the project’s existing fakes/proxies to make a collision spawn or teleport throw an unchecked exception. Assert the adapter throws `ShipRuntimeException` with the original exception as cause and removes entities created earlier in the same spawn attempt.

- [ ] **Step 3: Confirm focused failure**

```bash
./gradlew test --tests dev.mintychochip.ships.bukkit.BukkitCollisionVolumeManagerTest --tests dev.mintychochip.ships.bukkit.BukkitShipEntityCarrierTest
```

Expected: collision failure normalization assertions fail; carrier best-effort assertions continue passing.

- [ ] **Step 4: Add a narrow normalization helper per adapter**

Catch runtime failures at the adapter boundary, preserving an existing `ShipRuntimeException` rather than double-wrapping it. Include operation and ship ID in new exception messages. Do not catch JVM-fatal `Error` subclasses.

- [ ] **Step 5: Ensure partial collision spawns clean up**

Keep newly spawned volumes local until all flags/tags are applied. On failure, remove every local volume and attach cleanup failures as suppressed exceptions before throwing.

- [ ] **Step 6: Re-run focused tests**

Expected: PASS.

- [ ] **Step 7: Update the runtime spec and commit**

Check adapter normalization only after tests pass.

```bash
git add src/main/java/dev/jlo/ships/bukkit src/test/java/dev/jlo/ships/bukkit docs/specs/ship-runtime.md
git commit -m "fix: normalize runtime adapter failures"
```

### Task 5: Make Spawn and Move Rollback Total

**Files:**
- Modify: `src/main/java/dev/jlo/ships/ship/ShipRuntimeImpl.java`
- Modify: `src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipRuntimeImplTest.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java`
- Modify: `docs/specs/ship-runtime.md`

**Interfaces:**
- Consumes: Normalized adapter contract from Task 4.
- Produces: All-or-restored spawn/move/service assembly for runtime failures.

- [ ] **Step 1: Add runtime rollback tests**

Cover at least:

```text
spawn collision failure -> no renderer spawn; collision cleanup attempted
spawn renderer failure -> renderer cleanup then collision removal; cleanup failures suppressed
move-up carrier/collision failure -> pose, renderer, collision, and reversed carry return to old state
move-down collision failure -> renderer and pose return to old state; no carry occurs
remove/removeAll direct propagation remains explicit
```

- [ ] **Step 2: Add service assembly rollback tests**

Make `runtime.spawn` fail after world blocks are cleared. Assert exact block restoration, ship registry removal, buoyancy clear, runtime cleanup, persistence of the restored registry, and a reason-only `lastError` payload.

- [ ] **Step 3: Run focused tests and confirm failure**

```bash
./gradlew test --tests dev.mintychochip.ships.ship.ShipRuntimeImplTest --tests dev.mintychochip.ships.ship.ShipServiceImplTest
```

- [ ] **Step 4: Implement one rollback path per operation**

Preserve operation ordering from the spec. Roll back only steps known to have started, execute all cleanup steps, retain the original failure, and attach cleanup failures as suppressed exceptions.

- [ ] **Step 5: Make service rollback non-destructive to the original failure**

If block restoration, runtime removal, buoyancy clear, or persistence fails, keep trying remaining cleanup and attach failures. Return `null` only after cleanup completes; if state cannot be restored, throw `ShipRuntimeException` so plugin-level handling can disable safely.

- [ ] **Step 6: Re-run focused tests**

Expected: PASS.

- [ ] **Step 7: Update the runtime spec and commit**

```bash
git add src/main/java/dev/jlo/ships/ship/ShipRuntimeImpl.java src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java src/test/java/dev/jlo/ships/ship/ShipRuntimeImplTest.java src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java docs/specs/ship-runtime.md
git commit -m "fix: make ship runtime rollback total"
```

### Task 6: Harden Startup Reconciliation and Disable Cleanup

**Files:**
- Modify: `src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java`
- Modify: `src/main/java/dev/jlo/ships/ShipsPlugin.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java`
- Modify: `docs/specs/ship-runtime.md`

**Interfaces:**
- Consumes: `ShipRuntime.removeAllTagged()`, `spawn`, `remove`, `removeAll`.
- Produces: Startup failure always attempts full cleanup and plugin disable never skips the second cleanup action.

- [ ] **Step 1: Add reconciliation failure tests**

Test initial stale-sweep failure, middle-ship spawn failure, per-ship cleanup failure, final tag-sweep failure, and store-load failure. Assert the ship registry is empty and all available cleanup steps were attempted. Assert the thrown `IllegalStateException` names the failing phase/ship and preserves suppressed failures.

- [ ] **Step 2: Confirm tests fail**

```bash
./gradlew test --tests dev.mintychochip.ships.ship.ShipServiceImplTest
```

- [ ] **Step 3: Move the initial sweep inside the reconciliation boundary**

Treat load, initial sweep, and deterministic spawn as one operation. On any failure, remove every spawned ship, perform a tagged sweep, clear the model registry, and throw one `IllegalStateException`.

- [ ] **Step 4: Guard plugin disable cleanup per action**

Attempt `removeAllRuntime()` and `removeAllTagged()` independently. Log each failure with ship cleanup context; do not let the first prevent the second. Keep persistence unchanged on disable.

- [ ] **Step 5: Broaden enable failure handling to the service contract**

Ensure store adapter failures and reconciliation failures enter the same disable path. Do not catch unrelated fatal errors.

- [ ] **Step 6: Re-run focused tests and `./gradlew check`**

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 7: Update the spec and commit**

```bash
git add src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java src/main/java/dev/jlo/ships/ShipsPlugin.java src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java docs/specs/ship-runtime.md
git commit -m "fix: harden ship reconciliation cleanup"
```

### Task 7: Close Rider Tracker Lifecycle and Pose Window

**Files:**
- Modify: `src/main/java/dev/jlo/ships/bukkit/BukkitShipRiderTracker.java`
- Modify: `src/main/java/dev/jlo/ships/bukkit/BukkitShipEntityCarrier.java`
- Modify: `src/main/java/dev/jlo/ships/ship/ShipEntityCarrier.java`
- Modify: `src/main/java/dev/jlo/ships/ship/ShipRuntimeImpl.java`
- Modify: `src/main/java/dev/jlo/ships/ShipsPlugin.java`
- Modify: `src/test/java/dev/jlo/ships/bukkit/BukkitShipEntityCarrierTest.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipRuntimeImplTest.java`
- Modify: `docs/specs/ship-runtime.md`

**Interfaces:**
- Consumes: Existing `carry(Ship, double oldY, double newY)` path.
- Produces: Explicit carrier lifecycle hooks and a single pose basis during seed/update/carry.

- [ ] **Step 1: Define lifecycle methods**

Extend `ShipEntityCarrier` with `track(Ship, double poseY)`, `untrack(Ship)`, and `clear()` operations. Update `NoopShipEntityCarrier` in the same change; do not introduce a second lifecycle abstraction.

- [ ] **Step 2: Add failing tests**

Assert spawn seeds at the old/current committed pose, remove untracks the ship, removeAll/disable clears tracker state, and event-driven updates cannot switch to `ship.pose().y()` while a move is using `oldY`.

- [ ] **Step 3: Confirm focused failure**

```bash
./gradlew test --tests dev.mintychochip.ships.bukkit.BukkitShipEntityCarrierTest --tests dev.mintychochip.ships.ship.ShipRuntimeImplTest
```

- [ ] **Step 4: Implement explicit lifecycle wiring**

Wire tracking after successful runtime spawn, untracking on remove, and clearing on complete runtime cleanup. Use the pose supplied by the move transaction for both seed and overlap checks; do not read a concurrently changing pose for the same update.

- [ ] **Step 5: Register tracker before reconciliation only if lifecycle cleanup is safe**

Choose registration order based on required events during load. Document and test the choice; avoid leaving a loaded ship untracked after enable.

- [ ] **Step 6: Re-run focused tests and update spec**

Expected: PASS. Check both tracker lifecycle and pose-window items.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/jlo/ships/bukkit src/main/java/dev/jlo/ships/ship src/main/java/dev/jlo/ships/ShipsPlugin.java src/test/java/dev/jlo/ships/bukkit src/test/java/dev/jlo/ships/ship docs/specs/ship-runtime.md
git commit -m "fix: bind rider tracking to ship lifecycle"
```

**Stage gate:** Run `./gradlew check`; expected `BUILD SUCCESSFUL`. Then run a Paper smoke scenario: enable with a persisted ship, force reload failure in a disposable server, verify no tagged entities remain, then disable normally and verify cleanup completes.

---

## Stage 4 — Behavioral Proof and Cleanup

### Task 8: Make Collision Movement and Identity Verifiable

**Files:**
- Modify: `src/main/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManager.java`
- Modify: `src/test/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManagerTest.java`
- Modify: `docs/specs/ship-runtime.md`

**Interfaces:**
- Consumes: `ShipTransform.collisionAnchor(...)`, PDC owner/block keys, collision scoreboard tag.
- Produces: Deterministic tagged Shulker state and no teleport when `floor(oldY) == floor(newY)`.

- [ ] **Step 1: Add behavioral manager tests**

Verify each spawned Shulker is invisible, invulnerable, silent, no-AI, gravity-off, collidable, `peek=0`, non-persistent, tagged by ship and relative block, and spawned at the canonical collision anchor. Verify remove and tagged sweep symmetry.

- [ ] **Step 2: Add authoritative-anchor movement tests**

Assert moves within the same floor anchor do not teleport volumes, crossing an integer boundary teleports every volume once, and rollback returns every volume to the old anchor.

- [ ] **Step 3: Confirm tests fail**

```bash
./gradlew test --tests dev.mintychochip.ships.bukkit.BukkitCollisionVolumeManagerTest
```

- [ ] **Step 4: Add the anchor-change guard**

Compare authoritative anchors, not raw fractional pose values. Skip all volume teleports when the authoritative anchor is unchanged.

- [ ] **Step 5: Re-run focused tests**

Expected: PASS.

- [ ] **Step 6: Update spec and commit**

```bash
git add src/main/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManager.java src/test/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManagerTest.java docs/specs/ship-runtime.md
git commit -m "fix: verify and guard collision volume movement"
```

### Task 9: Record Live Collision and Runtime Acceptance

**Files:**
- Create: `docs/superpowers/results/2026-08-16-spec-alignment-acceptance.md`
- Modify: `docs/specs/ship-runtime.md`

**Interfaces:**
- Consumes: A disposable Paper 26.2 server launched by `./gradlew runServer`.
- Produces: Reproducible observed evidence for production entity geometry.

- [ ] **Step 1: Prepare a fixed fixture**

Use a small exposed-block ship with top, bottom, and all four side faces reachable. Record server version, plugin commit, world coordinates, ship dimensions, and relevant config.

- [ ] **Step 2: Exercise six-direction collision behavior**

Observe and record: stand on exposed tops, walk into north/south/east/west faces, test underside blocking, attempt sprint/jump pass-through, and verify no placed barrier/deck blocks exist.

- [ ] **Step 3: Exercise runtime lifecycle**

Assemble, bob across a fractional pose without an integer anchor change, cross an integer anchor, restart, and disassemble. Record visual/hull alignment and stale-entity absence after each transition.

- [ ] **Step 4: Decide from evidence**

If Shulker geometry passes, check the live collision acceptance item. If it fails, leave the item open and add exact observed geometry, reproduction, and a replacement-hull design prerequisite; do not mark acceptance by inference.

- [ ] **Step 5: Commit evidence**

```bash
git add docs/superpowers/results/2026-08-16-spec-alignment-acceptance.md docs/specs/ship-runtime.md
git commit -m "test: record live ship runtime acceptance"
```

### Task 10: Complete Buoyancy Contract Coverage

**Files:**
- Modify: `src/test/java/dev/jlo/ships/buoyancy/BuoyancyEngineTest.java`
- Modify: `src/test/java/dev/jlo/ships/buoyancy/BuoyancyResolverTest.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java`
- Modify: `docs/specs/buoyancy.md`
- Modify: `docs/specs/ship-runtime.md`

**Interfaces:**
- Consumes: Existing geometry-equilibrium buoyancy contract.
- Produces: Regression evidence for every Current boundary without introducing per-material/rider-load behavior.

- [ ] **Step 1: Add missing `BuoyancyImpl` tests**

Cover disabled rise/tick/sink semantics, blocked rise preserving pose, blocked tick resetting velocity, lower and upper bob reflection, sub-0.001 threshold skipping path checks while storing velocity, runtime failure restoring pose, and air/water-only path boundaries.

- [ ] **Step 2: Add resolver boundary tests**

Cover fractional negative `anchorDy`, shifted per-block scan windows, sealed columns, and no-water equilibrium.

- [ ] **Step 3: Add service persistence tests**

Assert a tick persists exactly once iff any ship moved, toggle persists once, sink persists once on success and zero times on failure, and direct service tick behavior is documented when the global scheduler is disabled.

- [ ] **Step 4: Run focused tests**

```bash
./gradlew test --tests dev.mintychochip.ships.buoyancy.BuoyancyEngineTest --tests dev.mintychochip.ships.buoyancy.BuoyancyResolverTest --tests dev.mintychochip.ships.ship.ShipServiceImplTest
```

Expected: PASS.

- [ ] **Step 5: Resolve manual sink semantics**

Use the existing command contract as the boundary: public command accepts positive integers only; service rejects non-positive values defensively; successful sink leaves velocity semantics explicit. Add tests before changing behavior.

- [ ] **Step 6: Update specs and commit**

Check only the coverage/decision items proven by these tests.

```bash
git add src/test/java/dev/jlo/ships/buoyancy src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java docs/specs/buoyancy.md docs/specs/ship-runtime.md
git commit -m "test: cover current buoyancy contracts"
```

### Task 11: Align Command Errors and Coverage

**Files:**
- Modify: `src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java`
- Modify: `src/main/java/dev/jlo/ships/command/ShipCommand.java`
- Modify: `src/main/java/dev/jlo/ships/command/ShipTabCompleter.java`
- Modify: `src/main/java/dev/jlo/ships/command/TargetResolver.java`
- Modify: `src/test/java/dev/jlo/ships/command/ShipCommandTest.java`
- Create: `src/test/java/dev/jlo/ships/command/ShipTabCompleterTest.java`
- Create: `src/test/java/dev/jlo/ships/command/BukkitTargetResolverTest.java`
- Modify: `docs/specs/commands.md`

**Interfaces:**
- Consumes: Reason-only `ShipService.lastError()` contract from Task 1.
- Produces: One operation prefix per failure and complete current command-surface coverage.

- [ ] **Step 1: Add command failure tests**

Cover permission rejection for inspect/disassemble/buoyancy/sink, sink `0` and negative rejection, extra-argument behavior, no-target, air target, service failure for every mutating command, and exact user-facing text.

- [ ] **Step 2: Add tab-completer tests**

Verify first-argument case-insensitive prefix filtering, full list on empty prefix, and empty completion for later arguments. Keep permission filtering out unless the spec is deliberately changed.

- [ ] **Step 3: Add target-resolver tests**

Verify configured distance, null/air rejection, solid target coordinates, and target world UUID. Correct the `TargetResolver` Javadoc from “capturable” to “non-air target”; material/size policy belongs to the service.

- [ ] **Step 4: Confirm focused failures**

```bash
./gradlew test --tests 'dev.mintychochip.ships.command.*'
```

- [ ] **Step 5: Remove duplicate operation prefixes**

Change service failures to reasons such as `Path blocked`; keep `Cannot lower ship: ` in `ShipCommand`. Apply the same ownership rule consistently to all service-backed failures.

- [ ] **Step 6: Decide inspect output explicitly**

Retain current `Ship <8-char id> | blocks=<count>` unless a user-facing requirement demands owner/origin. Record the decision and close the open alignment item; do not add fields solely to match a stale dated document.

- [ ] **Step 7: Re-run command tests and commit**

```bash
git add src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java src/main/java/dev/jlo/ships/command src/test/java/dev/jlo/ships/command docs/specs/commands.md
git commit -m "fix: align ship command error contracts"
```

### Task 12: Remove Dead Deck Production Code and Wording

**Files:**
- Delete: `src/main/java/dev/jlo/ships/deck/DeckManager.java`
- Delete: `src/main/java/dev/jlo/ships/deck/DeckSurface.java`
- Delete: `src/main/java/dev/jlo/ships/bukkit/BukkitDeckSurface.java`
- Delete: `src/test/java/dev/jlo/ships/deck/DeckManagerTest.java`
- Delete: `src/test/java/dev/jlo/ships/deck/DeckSurfaceTest.java`
- Delete: `src/test/java/dev/jlo/ships/deck/DeckSurfaceTestHelper.java`
- Modify: `src/main/java/dev/jlo/ships/ship/ShipService.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `docs/specs/README.md`
- Modify: `docs/specs/ship-runtime.md`

**Interfaces:**
- Consumes: Confirmed absence of production deck wiring.
- Produces: One Shulker-hull runtime with no barrier/deck implementation residue.

- [ ] **Step 1: Reconfirm no non-deck production references**

Use language-server references for each deck type. Deletion is allowed only if references are limited to the deck package and its tests.

- [ ] **Step 2: Delete deck code and tests**

Remove the entire dead package and `BukkitDeckSurface`. Remove the unused `NoopDeck` test helper.

- [ ] **Step 3: Correct stale wording**

Change `ShipService.removeAllRuntime` Javadoc from “entities and barriers” to runtime entities. Change `plugin.yml` description so it does not promise “walkable decks”; describe persistent display-and-collision ships accurately.

- [ ] **Step 4: Run the complete quality gate**

```bash
./gradlew check
```

Expected: `BUILD SUCCESSFUL` and no deck package compilation/tests.

- [ ] **Step 5: Update living specs and commit**

Remove the known-debt entry, check dead-code cleanup, and preserve the historical explanation in the runtime decision log.

```bash
git add -A src/main/java/dev/jlo/ships src/test/java/dev/jlo/ships src/main/resources/plugin.yml docs/specs
git commit -m "refactor: remove legacy deck implementation"
```

**Stage gate:** `./gradlew check` succeeds; live collision acceptance is recorded; current command, buoyancy, persistence, runtime, and world-policy contracts have focused tests.

---

## Stage 5 — Future Buoyancy Alignment

### Task 13: Write the Per-Material and Rider-Load Design Before Implementation

**Files:**
- Create during execution: `docs/superpowers/specs/<execution-date>-buoyancy-mass-model-design.md`
- Modify: `docs/specs/buoyancy.md`
- Modify: `docs/specs/ship-model.md`

**Interfaces:**
- Consumes: Stable geometry sampling and rider tracking from earlier stages.
- Produces: An approved contract for mass, displacement, equilibrium solving, configuration, defaults, persistence impact, and entity loads. This task does not implement physics.

- [ ] **Step 1: Resolve material configuration**

Choose one explicit surface: per-material density table with a validated positive finite default density for unknown materials. Define namespaced material keys, normalization, duplicate handling, missing-key defaults, and invalid-value enable failure.

- [ ] **Step 2: Resolve rider load scope**

Define whether only tracked players count or mobs/items count, the default mass units, join/leave update timing, and behavior when teleport/carry is best-effort.

- [ ] **Step 3: Define equilibrium mathematically**

Specify the discrete or interpolated solver for:

```text
displacedVolume(y) * waterDensity = blockMass + riderMass
```

Define no-solution behavior for always-sinking ships, bounds, tolerance, monotonicity assumptions, and the surface-area/draft acceptance equation.

- [ ] **Step 4: Define state and persistence effects**

Decide whether densities are configuration-only, whether rider mass is runtime-only, how equilibrium recalculates after configuration reload, and whether `ships.json` schema changes. Prefer no schema change unless ship-specific mass overrides are required.

- [ ] **Step 5: Define behavioral tests and live acceptance**

Include equal-volume materials with different density, mixed-material aggregate mass, unknown-material fallback, overloaded ship with no equilibrium, rider boarding/unboarding draft change, footprint-dependent draft, restart at floated pose, and disassembly at authoritative anchor.

- [ ] **Step 6: Update living specs only after approval**

Promote resolved decisions from Open questions to the Decisions log. Keep implementation boxes unchecked and create a separate implementation plan for this feature.

- [ ] **Step 7: Commit the approved design**

```bash
git add docs/superpowers/specs docs/specs/buoyancy.md docs/specs/ship-model.md
git commit -m "docs: define buoyancy mass model"
```

---

## Final Verification Matrix

- [ ] `./gradlew check` returns `BUILD SUCCESSFUL` from a clean checkout.
- [ ] Scan results reject mutation and do not reflect source-list changes.
- [ ] Disabled primary world rejects assembly before scanning or mutation.
- [ ] Non-primary-world assembly has one documented, tested failure path.
- [ ] Spawn/move/reconciliation failure tests prove all reachable cleanup steps execute and preserve original causes.
- [ ] Plugin disable attempts registered-runtime and tagged-entity cleanup independently.
- [ ] Rider tracker state is removed on ship removal and plugin disable.
- [ ] Same-anchor fractional bobbing does not teleport collision volumes.
- [ ] Collision flags, anchors, tags, rollback, and tag cleanup have behavioral adapter tests.
- [ ] Live six-direction Shulker collision evidence is recorded; acceptance remains open if any check fails.
- [ ] Buoyancy Current statements have focused tests and no longer promise unimplemented rider load or per-material density.
- [ ] Command failures contain exactly one operation prefix; all subcommand permissions and sink boundaries are tested.
- [ ] No production or test `deck` package remains; no barrier/walkable-deck wording remains in current code/specs.
- [ ] Living-spec dates, Current/Next checkboxes, decisions, and open questions match the verified repository state.
