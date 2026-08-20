# Streamed Collision Hull Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stream 1×1×1 Shulker cubes for exposed hull cells with a refcounted observer pool (mode B), keep today’s full spawn as an A/B control (mode A), and prove B uses fewer live volumes on a shared hypothetical fixture.

**Architecture:** Paper-free `ExposedCellIndex` answers AABB-to-AABB edge-distance queries. Paper-free `CollisionVolumePool` tracks observers per cell and emits spawn/share/despawn/show/hide decisions. `BukkitCollisionVolumeManager` owns both, defaults to streamed mode, and still offers full spawn for A. Carry stays on `TopSurfaceIndex`.

**Tech Stack:** Java 25, Gradle Kotlin DSL, Paper API 26.2, JUnit 5, existing Bukkit proxy tests.

**Spec:** `docs/superpowers/specs/2026-08-20-streamed-collision-hull-design.md`

## Global Constraints

- Default mode is B (streamed). A is a per-ship runtime control, not persisted.
- One Shulker per exposed cell, canonical `collisionAnchor`, existing PDC/tags/flags.
- Need test is AABB-to-AABB distance to cell edges: 0 if overlap, else Euclidean of positive axis separations. Enter ≤ 4, leave > 6.
- Players get `showEntity` / `hideEntity`. Items and mobs only keep cubes alive.
- Do not add `ShipConfig` keys. Named constants: enter 4, leave 6, default STREAMED.
- Do not add face types, merges, Interaction collision, or physics-octree player hulls.
- TDD: failing test first. Commit tests with the production change (always-green history).
- Do not stage unrelated dirty sail/command files.

---

### Task 1: Edge-distance cell index

**Files:**
- Create: `common/src/main/java/dev/mintychochip/archimedes/collision/CollisionBox.java`
- Create: `common/src/main/java/dev/mintychochip/archimedes/collision/ExposedCellIndex.java`
- Test: `common/src/test/java/dev/mintychochip/archimedes/collision/ExposedCellIndexTest.java`

**Constants (on `ExposedCellIndex`):** `ENTER_RANGE = 4.0`, `LEAVE_RANGE = 6.0`.

- [ ] **Step 1: Write failing index tests**

```java
@Test
void overlappingObserverNeedsTheCell() {
  ExposedCellIndex index = ExposedCellIndex.build(ship(List.of(new BlockPos(0, 0, 0))));
  CollisionBox observer = new CollisionBox(100.2, 200.2, 300.2, 100.8, 201.8, 300.8);
  assertEquals(List.of(new BlockPos(0, 0, 0)), index.cellsWithin(observer, 0, 0, 0, 4.0));
}

@Test
void farObserverIsOutsideEnterRange() {
  ExposedCellIndex index = ExposedCellIndex.build(ship(List.of(new BlockPos(0, 0, 0))));
  CollisionBox observer = new CollisionBox(120, 200, 300, 120.6, 201.8, 300.6);
  assertEquals(List.of(), index.cellsWithin(observer, 0, 0, 0, 4.0));
}

@Test
void hysteresisKeepsCellBetweenEnterAndLeave() {
  ExposedCellIndex index = ExposedCellIndex.build(ship(List.of(new BlockPos(0, 0, 0))));
  // 5 blocks east of the cell's max X (101): still inside leave 6, outside enter 4
  CollisionBox observer = new CollisionBox(106, 200, 300, 106.6, 201.8, 300.6);
  assertEquals(List.of(), index.cellsWithin(observer, 0, 0, 0, 4.0));
  assertEquals(List.of(new BlockPos(0, 0, 0)), index.cellsWithin(observer, 0, 0, 0, 6.0));
}
```

Ship helper matches `CollisionHullTest` (origin 100,200,300). Cell box at pose zero is `[100,101]×[200,201]×[300,301]`.

- [ ] **Step 2: Run RED**

Run: `./gradlew :common:test --tests '*ExposedCellIndexTest'`
Expected: compilation fails because the types do not exist.

- [ ] **Step 3: Implement `CollisionBox` and `ExposedCellIndex`**

`CollisionBox.distance(CollisionBox)`: per axis `gap = max(0, max(a.min-b.max, b.min-a.max))`, then `sqrt(gx²+gy²+gz²)`.

`ExposedCellIndex.build(Vehicle)` uses `CollisionHull.exposedBlocks`, stores each relative cell and its pose-zero world box (`origin + relative` to `+1`). `cellsWithin(observer, poseX, poseY, poseZ, range)` shifts each cell box by pose and returns cells with `distance <= range`, sorted lexicographically like `CollisionHull`. `bounds(poseX,poseY,poseZ)` is the union of cell boxes.

- [ ] **Step 4: Run GREEN**

Run: `./gradlew :common:test --tests '*ExposedCellIndexTest' --tests '*CollisionHullTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/archimedes/collision/CollisionBox.java \
  common/src/main/java/dev/mintychochip/archimedes/collision/ExposedCellIndex.java \
  common/src/test/java/dev/mintychochip/archimedes/collision/ExposedCellIndexTest.java
git commit -m "feat: query hull cells by AABB edge distance"
```

---

### Task 2: Refcounted volume pool

**Files:**
- Create: `common/src/main/java/dev/mintychochip/archimedes/collision/CollisionVolumePool.java`
- Test: `common/src/test/java/dev/mintychochip/archimedes/collision/CollisionVolumePoolTest.java`

Pool is Paper-free. `reconcile(Map<UUID, Set<BlockPos>> desired, Set<UUID> players)` returns a `Diff`:

```java
public record Diff(
    Set<BlockPos> spawn,
    Set<BlockPos> despawn,
    Map<BlockPos, Set<UUID>> show,
    Map<BlockPos, Set<UUID>> hide) {}
```

`show`/`hide` contain only player ids. After `reconcile`, `live()` is the cells with refcount > 0, `observers(cell)` is the current set, `refcount(cell)` is its size.

- [ ] **Step 1: Write failing pool tests**

First observer of a cell → spawn that cell and show if player. Second observer of the same cell → empty spawn, show the new player only. Last observer leaving → despawn. Non-player observer → spawn, no show. Empty desired map → despawn everything previously live.

- [ ] **Step 2: Run RED**

Run: `./gradlew :common:test --tests '*CollisionVolumePoolTest'`
Expected: compilation fails.

- [ ] **Step 3: Implement the pool**

Keep `Map<BlockPos, Set<UUID>>` internally. Diff against `desired`. Apply the diff to the internal map so a second `reconcile` with the same desired is empty.

- [ ] **Step 4: Run GREEN**

Run: `./gradlew :common:test --tests '*CollisionVolumePoolTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/archimedes/collision/CollisionVolumePool.java \
  common/src/test/java/dev/mintychochip/archimedes/collision/CollisionVolumePoolTest.java
git commit -m "feat: refcount shared collision cell observers"
```

---

### Task 3: A/B fixture (current vs streamed)

**Files:**
- Test: `common/src/test/java/dev/mintychochip/archimedes/collision/CollisionAbFixtureTest.java`

This is the comparison the design requires. No production types beyond index + pool.

Fixture: 1-thick box, outer 20×8×5 relative cells from `(0,0,0)` to `(19,4,7)` inclusive, hollow interior. Origin `(0,0,0)`, pose zero. Mid-deck observer AABB covering a player standing on the interior of the top: e.g. min `(9.2, 5.0, 3.2)` max `(9.8, 6.8, 3.8)` (on top of y=4 cells).

- [ ] **Step 1: Write the failing A/B assertions**

```java
@Test
void streamedLiveCountIsAStrictSubsetOfFullSpawn() {
  Vehicle ship = boxHull();
  ExposedCellIndex index = ExposedCellIndex.build(ship);
  List<BlockPos> exposed = CollisionHull.exposedBlocks(ship);
  int aLive = exposed.size();
  CollisionBox observer = new CollisionBox(9.2, 5.0, 3.2, 9.8, 6.8, 3.8);
  List<BlockPos> bCells = index.cellsWithin(observer, 0, 0, 0, ExposedCellIndex.ENTER_RANGE);
  assertTrue(aLive > 0);
  assertTrue(bCells.size() < aLive);
  assertTrue(exposed.containsAll(bCells));
  CollisionVolumePool pool = new CollisionVolumePool();
  UUID player = UUID.randomUUID();
  pool.reconcile(Map.of(player, Set.copyOf(bCells)), Set.of(player));
  assertEquals(bCells.size(), pool.live().size());
  pool.reconcile(Map.of(), Set.of());
  assertEquals(0, pool.live().size());
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :common:test --tests '*CollisionAbFixtureTest'`
Expected: FAIL if the neighborhood is not smaller than exposed, or FAIL to compile if `live()` is missing.

If the hollow-box neighborhood is not smaller, shrink the observer or enlarge the box until B < A still matches a real mid-deck player. Do not weaken the assertion to `<=` without the empty-observer `0` check.

- [ ] **Step 3: Fix index/pool only if the fixture exposes a bug**

No new production types. If GREEN already, do not add code.

- [ ] **Step 4: Run GREEN**

Run: `./gradlew :common:test --tests '*CollisionAbFixtureTest'`
Expected: PASS, and printed or asserted `aLive` is the exposed shell count.

- [ ] **Step 5: Commit**

```bash
git add common/src/test/java/dev/mintychochip/archimedes/collision/CollisionAbFixtureTest.java
git commit -m "test: compare full hull spawn against streamed neighborhood"
```

---

### Task 4: Manager mode B spawn-on-demand

**Files:**
- Modify: `api/src/main/java/dev/mintychochip/archimedes/collision/CollisionVolumeManager.java`
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/bukkit/BukkitCollisionVolumeManager.java`
- Test: `paper/src/test/java/dev/mintychochip/archimedes/bukkit/BukkitCollisionVolumeManagerTest.java`
- Create if needed: `common/src/main/java/dev/mintychochip/archimedes/collision/CollisionMode.java`
- Create if needed: `common/src/main/java/dev/mintychochip/archimedes/collision/CollisionSnapshot.java`

API additions on `CollisionVolumeManager` (default methods where existing fakes should keep compiling):

```java
enum CollisionMode { FULL, STREAMED }

record CollisionSnapshot(CollisionMode mode, int live, int exposed, int visibleToPlayer) {}

record CollisionObserver(UUID id, boolean player, CollisionBox box) {}

default CollisionMode mode(UUID shipId) { return CollisionMode.STREAMED; }
default void setMode(Vehicle ship, CollisionMode mode) { }
default CollisionSnapshot snapshot(UUID shipId, UUID playerId) {
  return new CollisionSnapshot(CollisionMode.STREAMED, 0, 0, 0);
}
default void observe(Vehicle ship, Collection<CollisionObserver> observers) { }
```

`spawn(Vehicle)` in STREAMED builds the index and leaves live count 0. `observe` runs the pool and spawns/despawns/shows. `setMode(FULL)` spawns every exposed cell globally visible (`visibleByDefault true`, no per-player hide). `setMode(STREAMED)` despawns cells with no observers. `move` teleports only the live map (FULL live map is all exposed).

- [ ] **Step 1: Write failing adapter tests**

Existing spawn test stays the FULL-path check: call `setMode(ship, FULL)` then `spawn`, or spawn in FULL. Add:

- STREAMED `spawn` does not call `world.spawn`.
- After `observe` with a player box overlapping the single block, one Shulker is spawned at the canonical anchor with existing flags, `setVisibleByDefault(false)`, and `showEntity` is not required on World (player show happens if a Player object is available; if the manager only records observer ids, assert live==1 and snapshot visibleToYou==1).
- Two observers on the same cell spawn once.
- `observe` empty list despawns.

Because Bukkit `Player.showEntity` needs a Player, keep visibility as: volumes `setVisibleByDefault(false)` in B; `show`/`hide` applied when a `Player` is later resolved. For unit tests without Player, assert `setVisibleByDefault(false)` and snapshot counts. Wire actual `showEntity` in Task 5 if Player lookup needs the world.

Simplest B visibility for this task: on spawn in B, `setVisibleByDefault(false)`. Snapshot `visibleToPlayer` = live cells whose observer set contains that player id.

- [ ] **Step 2: Run RED**

Run: `./gradlew :paper:test --tests '*BukkitCollisionVolumeManagerTest'`
Expected: existing spawn test may fail if default mode is STREAMED (no spawn). Update that test to set FULL **in the test first** — that is the A path. New B tests fail until implemented.

- [ ] **Step 3: Implement mode + pool in the Bukkit manager**

Per-ship: `CollisionMode`, `ExposedCellIndex`, `CollisionVolumePool`, live `Map<BlockPos, CollisionVolume>`. Default STREAMED. Existing spawn flags unchanged. B spawn: `setVisibleByDefault(false)` and `setPersistent(false)` plus current tags.

- [ ] **Step 4: Run GREEN**

Run: `./gradlew :paper:test --tests '*BukkitCollisionVolumeManagerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/dev/mintychochip/archimedes/collision \
  common/src/main/java/dev/mintychochip/archimedes/collision/CollisionMode.java \
  common/src/main/java/dev/mintychochip/archimedes/collision/CollisionSnapshot.java \
  paper/src/main/java/dev/mintychochip/archimedes/bukkit/BukkitCollisionVolumeManager.java \
  paper/src/test/java/dev/mintychochip/archimedes/bukkit/BukkitCollisionVolumeManagerTest.java
git commit -m "feat: stream collision cubes for nearby observers"
```

---

### Task 5: Sample observers and reconcile on move

**Files:**
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/bukkit/BukkitCollisionVolumeManager.java`
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/ArchimedesPlugin.java` if a listener must be registered
- Create: `paper/src/main/java/dev/mintychochip/archimedes/bukkit/BukkitCollisionObserverSampler.java`
- Test: `paper/src/test/java/dev/mintychochip/archimedes/bukkit/BukkitCollisionObserverSamplerTest.java`

Sampler: given ship bounds expanded by `LEAVE_RANGE` and a world, collect entities whose AABB intersects that box, skip tagged collision/render entities (same keys as the rider tracker), map each to `CollisionObserver` with `player = entity instanceof Player`. `move(ship)` after teleporting live volumes re-samples and `observe`s.

Reconcile also from a listener: `PlayerMoveEvent` only when the player crosses a block boundary; `PlayerQuitEvent` / `PlayerTeleportEvent` / `EntityDeathEvent` / `ItemSpawnEvent` as the rider tracker already does. Do not scan on every tiny move.

- [ ] **Step 1: Write failing sampler tests** with World/Entity proxies: a player inside expanded bounds is returned as `player=true`; a Shulker with the collision owner key is skipped; an item inside bounds is `player=false`.

- [ ] **Step 2: Run RED**

Run: `./gradlew :paper:test --tests '*BukkitCollisionObserverSamplerTest'`

- [ ] **Step 3: Implement sampler, call it from `spawn` (B) and `move`**

Register the listener in `ArchimedesPlugin` next to the rider tracker.

- [ ] **Step 4: Run GREEN** including existing manager tests.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: sample nearby entities as collision observers"
```

---

### Task 6: A/B command and inspect line

**Files:**
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/command/ShipCommand.java`
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/command/ShipTabCompleter.java`
- Modify: `paper/src/main/resources/plugin.yml`
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/ArchimedesPlugin.java` (pass manager into command)
- Test: `paper/src/test/java/dev/mintychochip/archimedes/command/ShipCommandTest.java`
- Test: `paper/src/test/java/dev/mintychochip/archimedes/command/ShipTabCompleterTest.java`
- Modify: `docs/specs/commands.md`
- Modify: `docs/specs/ship-runtime.md`

`/arch collision a|b` (also `full|streamed`) requires `player.isOp()`, targets nearby hull, calls `setMode`. Success: `Collision mode B (streamed).` Failure: `No ship nearby.` / `Only operators can switch collision mode.`

Inspect appends `collision=B live=N exposed=M visibleToYou=K` using `snapshot(ship.id(), player.getUniqueId())`. Permission `archimedes.collision` default true is unnecessary if op-only; skip a new node, use op check like `kill all`.

- [ ] **Step 1: Failing command and tab tests**
- [ ] **Step 2: RED**
- [ ] **Step 3: Implement command, tab, inspect line, plugin wiring, living specs**
- [ ] **Step 4: GREEN**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat: switch and inspect streamed vs full collision"
```

---

## Self-review

- Spec A/B fixture → Task 3.
- Edge distance + hysteresis → Task 1.
- Refcount share/despawn → Task 2.
- Default B, A control, Shulker flags, snapshot → Task 4.
- Observers including items/mobs, move reconcile → Task 5.
- Command + inspect + living specs → Task 6.
- Non-goals (face types, merges, ShipConfig) have no tasks.
