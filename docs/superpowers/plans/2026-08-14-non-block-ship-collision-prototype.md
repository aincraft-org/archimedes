# Non-Block Ship Collision Prototype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. This plan intentionally stops at a runtime spike; it does not authorize production integration until the pass/fail decision is recorded.

**Goal:** Test invisible Shulkers as player-solid ship collision volumes without placing hull barriers or captured blocks into the world.

**Architecture:** Add a collision-volume abstraction independent of `BlockDisplay` rendering and `DeckManager`. Implement a Bukkit Shulker adapter for a temporary debug fixture only. Spawn, move, tag, and remove collision entities by ship UUID. Keep production assembly and buoyancy unchanged until live movement evidence passes.

**Tech Stack:** Paper 26.2, Java 25, Gradle, Bukkit/Paper entities, JUnit 5, existing `RenderSurface`/Bukkit adapter patterns.

## Global Constraints

- Paper target: `26.2`; Java: `25`.
- No `Material.BARRIER` hull collision.
- No captured hull blocks written by the spike.
- Existing deck barriers remain separate and unchanged.
- The spike must allow water beside the test volume without replacing it.
- Runtime player movement is required; unit tests alone cannot establish collision correctness.
- No production assembly, buoyancy, persistence, passenger, horizontal movement, or fractional collision integration until the spike passes.
- Every test command must be focused; run the full quality gate once at the end.

---

### Task 1: Define collision-volume contract

**Files:**
- Create: `src/main/java/dev/jlo/ships/collision/CollisionVolume.java`
- Create: `src/main/java/dev/jlo/ships/collision/CollisionVolumeManager.java`
- Test: `src/test/java/dev/jlo/ships/collision/CollisionVolumeTest.java`

**Interfaces:**
- `CollisionVolume` exposes `UUID shipId()`, `void move(int x, int y, int z)`, and `void remove()`.
- `CollisionVolumeManager` exposes `CollisionVolume spawn(UUID shipId, Location location)` and `void remove(UUID shipId)`.
- The contract must not import `DeckManager` or `ShipRendererLike`.

- [ ] **Step 1: Write failing contract tests**

Test that a fake manager can represent a volume, move its integer anchor, and remove it by ship UUID. Test that removing one UUID does not remove another.

- [ ] **Step 2: Run focused test**

Run:

```bash
./gradlew test --tests dev.jlo.ships.collision.CollisionVolumeTest
```

Expected: FAIL because the collision package does not exist.

- [ ] **Step 3: Add minimal interfaces and fake test implementation**

Keep production interfaces free of Bukkit-specific details except for the location type required by the adapter boundary. Do not add ship-service wiring.

- [ ] **Step 4: Run focused test**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/jlo/ships/collision src/test/java/dev/jlo/ships/collision
git commit -m "test: define ship collision volume contract"
```

---

### Task 2: Implement invisible Shulker adapter

**Files:**
- Create: `src/main/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManager.java`
- Create: `src/main/java/dev/jlo/ships/bukkit/BukkitShulkerCollisionVolume.java`
- Modify: `src/main/java/dev/jlo/ships/ShipsPlugin.java` only if needed to construct the debug manager.
- Test: `src/test/java/dev/jlo/ships/bukkit/BukkitCollisionVolumeManagerTest.java`

**Interfaces:**
- Constructor accepts `World`, `NamespacedKey`, and no ship service.
- `spawn(UUID, Location)` creates an invisible Shulker and returns a `CollisionVolume`.
- The entity must be configured with AI disabled, invisible, invulnerable, silent, gravity disabled, collidable enabled, and closed peek state using the actual Paper 26.2 API signature.
- Add a scoreboard/PDC ownership tag containing the ship UUID.

- [ ] **Step 1: Compile-probe the Paper 26.2 Shulker API**

Use IDE/LSP or a tiny focused source probe to confirm the exact return type and parameter type of `Shulker.setPeek(...)`, plus availability of `setCollidable`, `setGravity`, `setAI`, and `setInvisible`.

Do not infer signatures from another Minecraft version.

- [ ] **Step 2: Write failing adapter tests**

Use a fake or proxy `Shulker` to assert every required configuration flag, ownership tag, and move/remove delegation.

- [ ] **Step 3: Run focused tests**

```bash
./gradlew test --tests dev.jlo.ships.bukkit.BukkitCollisionVolumeManagerTest
```

Expected: FAIL until the adapter exists.

- [ ] **Step 4: Implement the adapter**

Use `world.spawn(location, Shulker.class, consumer)`. Do not use armor stands or interaction entities. Do not set `noPhysics`; the entity must remain collision-capable. Do not add event listeners unless the runtime test demonstrates unwanted interaction.

- [ ] **Step 5: Run focused tests**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/jlo/ships/bukkit src/test/java/dev/jlo/ships/bukkit
git commit -m "feat: prototype shulker ship collision"
```

---

### Task 3: Add temporary runtime fixture

**Files:**
- Modify: `src/main/java/dev/jlo/ships/ShipsPlugin.java`
- Modify: `src/main/java/dev/jlo/ships/command/ShipCommand.java`
- Modify: `src/main/resources/plugin.yml`
- Test: `src/test/java/dev/jlo/ships/command/ShipCommandTest.java`

**Interfaces:**
- Add an explicitly debug-only command such as `/ship collision-test` behind the existing operator permission.
- The command spawns the smallest one-volume Shulker fixture at the targeted location and reports its UUID.
- A second invocation moves the fixture by one integer block or a separate `/ship collision-test move` subcommand moves it.
- A cleanup subcommand removes the fixture.
- The fixture must not register the volume as a production ship or modify `ships.json`.

- [ ] **Step 1: Write failing command tests**

Assert permission rejection, spawn delegation, move delegation, and cleanup delegation. Assert no production ship persistence call occurs.

- [ ] **Step 2: Run focused command tests**

```bash
./gradlew test --tests dev.jlo.ships.command.ShipCommandTest
```

Expected: FAIL for the new subcommand behavior.

- [ ] **Step 3: Implement the debug fixture path**

Keep it isolated from `ShipServiceImpl`, `BuoyancyImpl`, and normal assembly. Add a clear debug-only response so the operator knows the entity UUID and cleanup command.

- [ ] **Step 4: Run focused command tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/jlo/ships/ShipsPlugin.java src/main/java/dev/jlo/ships/command/ShipCommand.java src/main/resources/plugin.yml src/test/java/dev/jlo/ships/command/ShipCommandTest.java
git commit -m "feat: add collision volume debug fixture"
```

---

### Task 4: Run live Paper collision experiment

**Files:**
- No production files unless the runtime test reveals a narrowly scoped adapter defect.
- Create: `docs/superpowers/results/2026-08-14-non-block-collision-spike.md`

- [ ] **Step 1: Build and start the managed server**

```bash
./gradlew check
```

Then restart the managed `paper` process and wait for the `Done (...)!` readiness log.

- [ ] **Step 2: Execute exact runtime cases**

Record results for:

1. Face collision from north, south, east, west, above, and below.
2. Water adjacent to the volume; confirm water remains unchanged.
3. One-block integer move; confirm no old-location collision remains.
4. Rectangular multi-block fixture; record gaps and over-blocking.
5. Invisible/silent/no-AI/invulnerable/gravity-free behavior.
6. Explicit cleanup and plugin restart cleanup.

- [ ] **Step 3: Record pass/fail evidence**

The result file must include server build/version, command sequence, observed player movement, entity counts before/after cleanup, and the hitbox mismatch decision. Do not claim a pass based only on entity flags or `getBoundingBox()`.

- [ ] **Step 4: Commit the result**

```bash
git add docs/superpowers/results/2026-08-14-non-block-collision-spike.md
git commit -m "docs: record non-block collision spike"
```

---

### Task 5: Make the architecture decision

**Files:**
- Modify: `docs/superpowers/specs/2026-08-14-non-block-ship-collision-prototype.md`
- Create: `docs/superpowers/specs/2026-08-14-authoritative-ship-body-design.md` only if the spike fails.

- [ ] **Step 1: Evaluate evidence against acceptance criteria**

Pass requires all of the following: player blocked from every tested face, water unchanged, integer movement correct, no stale entity, invisible/non-interactive behavior, complete cleanup, acceptable rectangular hitbox, and no hull barriers.

- [ ] **Step 2: If pass, write a production follow-up plan**

The follow-up must define collision-volume ownership, entity persistence/reconciliation, movement ordering, event cancellation, and multi-volume hull approximation. Do not integrate it in this spike.

- [ ] **Step 3: If fail, reject entity collision and document the real-block fallback**

The fallback must use exact captured block data, integer anchors, water-allowed destination validation, ownership tracking, and atomic rollback. Do not leave partial mutator changes in the tree.

- [ ] **Step 4: Commit the decision**

```bash
git add docs/superpowers/specs
git commit -m "docs: decide ship collision architecture"
```

---

## Final verification

After all spike tasks:

```bash
./gradlew test
./gradlew check
```

Then verify the managed Paper server starts, Ships enables, and the result document contains an explicit pass/fail decision. No production buoyancy or assembly claim is allowed until the decision is recorded.
