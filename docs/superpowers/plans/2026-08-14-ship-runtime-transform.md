# Ship Runtime Transform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace barrier-backed ships with origin-aligned BlockDisplays and ship-owned entity collision volumes that move and reconstruct with the persisted ship.

**Architecture:** `ShipTransform` is the sole coordinate projection from origin, relative block, and pose. Rendering recomputes locations from the model; a production collision hull manager owns tagged Shulkers per exposed hull block; `ShipRuntime` composes rendering and collision lifecycle. Buoyancy validates block-world clearance and moves runtime atomically without deck supports.

**Tech Stack:** Java 21, Paper 26.2 API, JUnit 5, Gradle, Spotless, Checkstyle, PMD, SpotBugs.

## Global Constraints

- Production ship collision uses entity hulls and no barrier blocks.
- Visual projection uses block-corner coordinates without implicit half-block offsets.
- Authoritative cells use `Math.floor(ShipPose.y())`.
- Runtime identities contain ship ID and stable relative block position.
- Runtime reconstruction is deterministic and idempotent.
- Every behavior change is developed test-first and committed atomically.

---

### Task 1: Canonical Ship Transform and Drift-Free Rendering

**Files:**
- Create: `src/main/java/dev/jlo/ships/model/ShipTransform.java`
- Modify: `src/main/java/dev/jlo/ships/render/RenderSurface.java`
- Modify: `src/main/java/dev/jlo/ships/bukkit/BukkitShipRenderer.java`
- Test: `src/test/java/dev/jlo/ships/model/ShipTransformTest.java`
- Test: `src/test/java/dev/jlo/ships/render/ShipRendererTest.java`

**Interfaces:**
- Produces: `ShipTransform.visual(Ship, BlockPos): VisualPosition`
- Produces: `ShipTransform.cell(Ship, BlockPos): BlockPos`
- Produces: `RenderSurface.location(ShipOrigin, double, double, double)` as an exact absolute block-corner projection.
- Consumers: collision hull, buoyancy clearance, renderer, world restoration.

- [ ] **Step 1: Write failing transform tests**

Assert origin `(100,200,300)`, relative `(2,-1,3)`, and pose `1.75` project visually to `(102,200.75,303)` and authoritatively to `(102,200,303)`. Add negative-pose coverage proving `-0.25` floors to `-1`.

- [ ] **Step 2: Write failing renderer tests**

Assert initial BlockDisplay locations are integer-aligned block corners and two consecutive reposition calls preserve exact X/Z while Y equals the model projection.

- [ ] **Step 3: Run tests and verify red**

Run: `./gradlew test --tests dev.jlo.ships.model.ShipTransformTest --tests dev.jlo.ships.render.ShipRendererTest`

Expected: failures showing the existing `+0.5` projection and missing canonical transform.

- [ ] **Step 4: Implement canonical projection**

Add an immutable transform utility whose visual projection uses fractional pose and whose cell projection uses `pose.anchorDy()`. Remove `+0.5` from `RenderSurface.of().location`. Change `BukkitShipRenderer.reposition` to pair tagged displays with stable block identities and recompute each destination from the ship model rather than current entity locations.

- [ ] **Step 5: Run focused tests and commit**

Run: `./gradlew spotlessApply test --tests dev.jlo.ships.model.ShipTransformTest --tests dev.jlo.ships.render.ShipRendererTest`

Expected: PASS.

Commit: `fix: align ship runtime transforms`

### Task 2: Deterministic Exposed Collision Hull

**Files:**
- Create: `src/main/java/dev/jlo/ships/collision/CollisionHull.java`
- Modify: `src/main/java/dev/jlo/ships/collision/CollisionVolume.java`
- Modify: `src/main/java/dev/jlo/ships/collision/CollisionVolumeManager.java`
- Modify: `src/main/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManager.java`
- Test: `src/test/java/dev/jlo/ships/collision/CollisionHullTest.java`
- Test: `src/test/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManagerTest.java`

**Interfaces:**
- Produces: `CollisionHull.exposedBlocks(Ship): List<BlockPos>` sorted lexicographically.
- Produces: `CollisionVolumeManager.spawn(Ship): void`, `move(Ship): void`, `remove(UUID): void`, `removeAll(): void`.
- Consumes: `ShipTransform.cell(Ship, BlockPos)`.

- [ ] **Step 1: Write failing hull tests**

Cover a single block, a solid `3×3×3` cube that excludes its center, and deterministic ordering. Every returned block must have at least one unoccupied six-direction neighbor.

- [ ] **Step 2: Write failing lifecycle contract tests**

Use an in-memory manager to prove all expected relative keys spawn once, moving to a new anchor updates every volume, duplicate spawn replaces/reconciles existing volumes, removing one ship leaves another intact, and `removeAll` cleans all tracked volumes.

- [ ] **Step 3: Run tests and verify red**

Run: `./gradlew test --tests dev.jlo.ships.collision.CollisionHullTest --tests dev.jlo.ships.bukkit.BukkitCollisionVolumeManagerTest`

Expected: failures for missing hull and ship lifecycle methods.

- [ ] **Step 4: Implement the entity hull manager**

Replace the one-volume debug map with `Map<UUID, Map<BlockPos, CollisionVolume>>`. Spawn one Shulker per exposed block, tag it with ship ID and stable `x,y,z` relative key, use explicit centered X/Z collision anchors derived from `ShipTransform.cell`, and set it non-persistent. Make spawn all-or-nothing and cleanup partial entities on failure.

- [ ] **Step 5: Run focused tests and commit**

Run: `./gradlew spotlessApply test --tests dev.jlo.ships.collision.CollisionHullTest --tests dev.jlo.ships.bukkit.BukkitCollisionVolumeManagerTest`

Expected: PASS.

Commit: `feat: add deterministic ship collision hulls`

### Task 3: Production Runtime Lifecycle Without Barriers

**Files:**
- Create: `src/main/java/dev/jlo/ships/ship/ShipRuntime.java`
- Create: `src/main/java/dev/jlo/ships/ship/ShipRuntimeImpl.java`
- Modify: `src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java`
- Modify: `src/main/java/dev/jlo/ships/ShipsPlugin.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java`
- Create: `src/test/java/dev/jlo/ships/ship/ShipRuntimeImplTest.java`

**Interfaces:**
- Produces: `ShipRuntime.spawn(Ship)`, `move(Ship, double, double)`, `remove(Ship)`, and `removeAll(Collection<Ship>)`.
- Composes: `ShipRendererLike` and `CollisionVolumeManager`.
- Removes: production `DeckManager` dependency from `ShipServiceImpl`.

- [ ] **Step 1: Write failing runtime transaction tests**

Prove spawn creates both displays and hull, renderer failure removes a spawned hull, collision failure does not render, movement updates displays and hull, movement failure restores old positions, and remove cleans both components.

- [ ] **Step 2: Write failing service lifecycle tests**

Prove assembly never invokes any barrier/deck surface, assembly rollback removes runtime, disassembly removes runtime after restoration, disable removes runtime while keeping persistence, and load reconstructs runtime for every persisted ship.

- [ ] **Step 3: Run tests and verify red**

Run: `./gradlew test --tests dev.jlo.ships.ship.ShipRuntimeImplTest --tests dev.jlo.ships.ship.ShipServiceImplTest`

Expected: failures because service still requires `DeckManager` and has no collision lifecycle.

- [ ] **Step 4: Implement the runtime composition and clean cutover**

Construct one production collision manager in `ShipsPlugin`, compose it with the renderer, inject `ShipRuntime` into service and buoyancy, remove all production `deck.deploy/remove` calls, and reconstruct runtime during `loadAll`. Keep `/ship collision-test` isolated or remove it if its old manager API no longer matches; production ship ownership is authoritative.

- [ ] **Step 5: Run focused tests and commit**

Run: `./gradlew spotlessApply test --tests dev.jlo.ships.ship.ShipRuntimeImplTest --tests dev.jlo.ships.ship.ShipServiceImplTest`

Expected: PASS.

Commit: `feat: attach collision hulls to ship lifecycle`

### Task 4: Multi-Block Movement Transaction

**Files:**
- Modify: `src/main/java/dev/jlo/ships/buoyancy/BuoyancyImpl.java`
- Modify: `src/main/java/dev/jlo/ships/buoyancy/Buoyancy.java`
- Modify: `src/main/java/dev/jlo/ships/ship/ShipRuntime.java`
- Test: `src/test/java/dev/jlo/ships/buoyancy/BuoyancyEngineTest.java`

**Interfaces:**
- Consumes: `ShipTransform.cell` for path cells.
- Consumes: `ShipRuntime.move(Ship, oldY, newY)` for display and collision updates.
- Produces: atomic repeated and multi-block vertical moves without deck self-obstruction.

- [ ] **Step 1: Write failing movement tests**

Test sinking three blocks in one command, three consecutive one-block sinks, a clear negative-pose transition, obstruction at an intermediate cell, and runtime move failure rollback to the original pose.

- [ ] **Step 2: Run tests and verify red**

Run: `./gradlew test --tests dev.jlo.ships.buoyancy.BuoyancyEngineTest`

Expected: failures showing current deck coupling and missing runtime transaction behavior.

- [ ] **Step 3: Implement canonical clearance and runtime movement**

Remove `DeckManager` from `BuoyancyImpl`. Generate every traversed authoritative block cell with `ShipTransform`; allow only air or water. Change pose and runtime together, restoring the old pose and runtime state if movement fails. Reset velocity after manual sinking.

- [ ] **Step 4: Run focused and aggregate tests**

Run: `./gradlew spotlessApply test --tests dev.jlo.ships.buoyancy.BuoyancyEngineTest --tests dev.jlo.ships.ship.ShipServiceImplTest --tests dev.jlo.ships.render.ShipRendererTest`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit: `fix: make ship vertical movement transactional`

### Task 5: Runtime Reconciliation and Full Verification

**Files:**
- Modify: `src/main/java/dev/jlo/ships/ShipsPlugin.java`
- Modify: `src/main/java/dev/jlo/ships/ship/ShipServiceImpl.java`
- Modify: `src/test/java/dev/jlo/ships/ship/ShipServiceImplTest.java`
- Modify: `docs/superpowers/results/2026-08-14-non-block-collision-spike.md`

**Interfaces:**
- Startup loads persisted models and reconstructs exactly one display/collision runtime per expected relative block key.
- Disable removes every runtime entity while preserving `ships.json`.

- [ ] **Step 1: Add failing restart reconciliation tests**

Cover persisted ships with no entities, stale duplicate entities, partial collision spawn failure, and successful cleanup after startup failure.

- [ ] **Step 2: Implement deterministic reconciliation**

On enable, clean tagged stale runtime entities, load models, then spawn canonical runtime for each model. If any ship fails, remove all reconstructed runtime and fail plugin startup with the ship identifier in the error.

- [ ] **Step 3: Run complete quality gate**

Run: `./gradlew spotlessApply test check build`

Expected: BUILD SUCCESSFUL. Inspect Checkstyle, PMD, and SpotBugs reports; zero project-rule violations.

- [ ] **Step 4: Run live Paper acceptance scenario**

Build and restart the managed Paper server. Assemble a multi-block ship and verify with the connected client:

1. No `BARRIER` exists in the ship footprint or exposed deck support cells.
2. BlockDisplays occupy canonical integer block corners with no half-block visual shift.
3. Tagged collision Shulkers exist for that ship immediately after assembly.
4. The player can stand on exposed top surfaces and cannot pass through hull sides.
5. `/ship sink 3` moves displays and collision three cells while remaining aligned.
6. Three subsequent `/ship sink 1` commands continue moving when the path is clear.
7. Server restart reconstructs visual and collision runtime.
8. Disassembly restores exact blocks and removes all runtime entities.

- [ ] **Step 5: Record evidence and commit**

Update the result document with exact commands, observed server log lines, entity/block inspection, and any collision geometry limitations.

Commit: `test: verify entity-backed ship runtime`
