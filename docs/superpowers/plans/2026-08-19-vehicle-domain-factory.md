# Vehicle Domain and Factory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `Ship` with `Vehicle` as the physics-bearing domain type, and build each tick’s `Body` from a factory so the same hull can float, hover, sail, and thrust without a kind enum.

**Architecture:** `Vehicle` is the long-lived aggregate (hull, pose, actuator flags). `VehicleFactory` rebuilds an ephemeral `Body` each tick: every captured block is mass; waterline lift and envelope lift are intrinsic; sails/engines are flags that only gate `PressureSailForce` / `MediumThrustForce`. `/arch` stays the command. `archimedes.json` does not grow actuator fields.

**Tech Stack:** Java 25, Gradle (`:api`, `:common`, `:paper`), JUnit 5, JOML, existing `dev.mintychochip.phys` catalog (`MediumThrustForce` must already be on the tree).

**Spec:** `docs/superpowers/specs/2026-08-19-vehicle-domain-factory-design.md`

---

## Prerequisite

`MediumThrustForce` is required and is currently **uncommitted** working-tree work. Land it (and `DensitySampling`, density-scaled `QuadraticDragForce`) in its own commit from `docs/superpowers/plans/2026-08-17-medium-propulsion-plan.md` **before Task 1**. Do not mix catalog files into Vehicle commits.

## File Map

### Create

- `api/src/main/java/dev/mintychochip/archimedes/model/Vehicle.java` — renamed aggregate + sail/engine flags
- `api/src/test/java/dev/mintychochip/archimedes/model/VehicleTest.java` — flag defaults
- `common/src/main/java/dev/mintychochip/archimedes/phys/EnvelopeBuoyancyForce.java` — aerostatic lift from envelope volume only
- `common/src/test/java/dev/mintychochip/archimedes/phys/EnvelopeBuoyancyForceTest.java`
- `common/src/main/java/dev/mintychochip/archimedes/phys/VehicleFactory.java` — `buildBody`
- `common/src/test/java/dev/mintychochip/archimedes/phys/VehicleFactoryTest.java`

### Modify (rename + wire)

- `api/src/main/java/dev/mintychochip/archimedes/model/Ship.java` — delete after `Vehicle` exists
- Every production/test file that imports `dev.mintychochip.archimedes.model.Ship` (48 files; see Task 1)
- `api/.../config/ShipConfig.java` — engine/envelope materials + `engineThrust`
- `paper/.../config/ShipConfigLoader.java` + `paper/src/main/resources/config.yml`
- `paper/src/test/java/dev/mintychochip/archimedes/config/` loader tests
- `common/.../phys/ShipPhysicsImpl.java` — call factory; drop local `body()`
- `common/.../phys/ShipBody.java` — delete once factory owns construction
- `api/.../ship/ShipService.java` + `ShipServiceImpl` — `toggleSails` / `toggleEngines`
- `paper/.../command/ShipCommand.java`, `ShipTabCompleter.java`, `ShipCommandTest.java`
- `paper/src/main/resources/plugin.yml` — permissions + usage
- Living specs under `docs/specs/`

### Intentionally not renamed in this plan

`ShipBlock`, `ShipOrigin`, `ShipPose`, `ShipTransform`, `ShipRuntime`, `ShipPhysics`, `ShipStore`, `ShipService` type **names** stay except the aggregate `Ship` → `Vehicle`. They still *speak* `Vehicle` as the parameter. Pose/block/origin are hull snapshots, not the physics object. Full `ShipPhysics` → `VehiclePhysics` filename churn is a follow-up, not required for behavior.

## Global Constraints

- No `kind` enum. No `Airship` class.
- Every captured block adds mass. Flags never remove colliders.
- Envelope lift sums envelope cells only. Never `FluidBuoyancyForce(uniform air)` on the whole hull.
- Sail keys and envelope keys must be disjoint at config load.
- `/arch` is the command; `/ship` alias stays; no `/vehicle`.
- Do not write `sailsEnabled` / `enginesEnabled` into `archimedes.json`.
- TDD: failing test, then implementation, then commit.
- Match existing style: `final` classes, field javadocs, constructor javadocs, Spotless.
- Verify with `./gradlew :api:test :common:test :paper:test` after each task; do not “fix” pre-existing Checkstyle/PMD/SpotBugs in unrelated files.

---

### Task 1: Vehicle aggregate + actuator flags

**Files:**
- Create: `api/src/main/java/dev/mintychochip/archimedes/model/Vehicle.java`
- Create: `api/src/test/java/dev/mintychochip/archimedes/model/VehicleTest.java`
- Delete: `api/src/main/java/dev/mintychochip/archimedes/model/Ship.java`
- Modify: every `import ...model.Ship` / `Ship` type usage listed by `rg -l 'archimedes.model.Ship'` (api, common, paper)

- [ ] **Step 1: Write the failing Vehicle flag tests**

```java
package dev.mintychochip.archimedes.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleTest {
  @Test
  void actuatorFlagsDefaultOn() {
    Vehicle vehicle =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks")));
    assertTrue(vehicle.sailsEnabled());
    assertTrue(vehicle.enginesEnabled());
    assertTrue(vehicle.buoyancyEnabled());
  }

  @Test
  void actuatorFlagsToggleWithoutChangingBlockCount() {
    Vehicle vehicle =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
            List.of(
                new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks"),
                new ShipBlock(new BlockPos(0, 1, 0), "minecraft:white_wool")));
    vehicle.setSailsEnabled(false);
    vehicle.setEnginesEnabled(false);
    assertFalse(vehicle.sailsEnabled());
    assertFalse(vehicle.enginesEnabled());
    assertEquals(2, vehicle.blockCount());
  }
}
```

Add `import static org.junit.jupiter.api.Assertions.assertEquals;`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :api:test --tests dev.mintychochip.archimedes.model.VehicleTest`

Expected: FAIL compiling (`Vehicle` not found) or test class not found.

- [ ] **Step 3: Implement Vehicle and retarget call sites**

`git mv` `Ship.java` to `Vehicle.java`. Rename the class and constructors. Keep `buoyancyEnabled` as the physics kill switch. Add flags defaulting to true:

```java
public final class Vehicle {
  private final UUID id;
  private final UUID ownerId;
  private final ShipOrigin origin;
  private final List<ShipBlock> blocks;
  private ShipPose pose;
  private boolean buoyancyEnabled;
  private boolean sailsEnabled;
  private boolean enginesEnabled;

  public Vehicle(UUID id, UUID ownerId, ShipOrigin origin, List<ShipBlock> blocks) {
    this(id, ownerId, origin, blocks, new ShipPose(0), true);
  }

  public Vehicle(
      UUID id,
      UUID ownerId,
      ShipOrigin origin,
      List<ShipBlock> blocks,
      ShipPose pose,
      boolean buoyancyEnabled) {
    this(id, ownerId, origin, blocks, pose, buoyancyEnabled, true, true);
  }

  public Vehicle(
      UUID id,
      UUID ownerId,
      ShipOrigin origin,
      List<ShipBlock> blocks,
      ShipPose pose,
      boolean buoyancyEnabled,
      boolean sailsEnabled,
      boolean enginesEnabled) {
    this.id = id;
    this.ownerId = ownerId;
    this.origin = origin;
    this.blocks = List.copyOf(blocks);
    this.pose = pose;
    this.buoyancyEnabled = buoyancyEnabled;
    this.sailsEnabled = sailsEnabled;
    this.enginesEnabled = enginesEnabled;
  }

  public UUID id() { return id; }
  public UUID ownerId() { return ownerId; }
  public ShipOrigin origin() { return origin; }
  public List<ShipBlock> blocks() { return blocks; }
  public int blockCount() { return blocks.size(); }
  public void setPose(ShipPose newPose) { this.pose = newPose; }
  public ShipPose pose() { return pose; }
  public boolean buoyancyEnabled() { return buoyancyEnabled; }
  public void setBuoyancyEnabled(boolean enabled) { this.buoyancyEnabled = enabled; }
  public boolean sailsEnabled() { return sailsEnabled; }
  public void setSailsEnabled(boolean enabled) { this.sailsEnabled = enabled; }
  public boolean enginesEnabled() { return enginesEnabled; }
  public void setEnginesEnabled(boolean enabled) { this.enginesEnabled = enabled; }
}
```

Keep existing javadoc tone (field comments, constructor docs). Replace `Ship` type usages with `Vehicle` in all modules. `new Ship(` → `new Vehicle(`. Method names like `findOwnedInWorld` can stay. `ShipStore.parseShip` can be renamed `parseVehicle` internally; JSON keys stay.

Do **not** persist the new flags in `ShipStore.saveAll`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :api:test :common:test :paper:compileJava`

Expected: PASS (paper tests if compile is green; run `:paper:test` if compile works).

- [ ] **Step 5: Commit**

```bash
git add api common paper
git commit -m "feat: replace Ship with Vehicle and add actuator flags"
```

Do not stage `.superpowers/`, `FluidField.java.tmp`, or unrelated docs.

---

### Task 2: Config lists for engines and envelope

**Files:**
- Modify: `api/src/main/java/dev/mintychochip/archimedes/config/ShipConfig.java`
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/config/ShipConfigLoader.java`
- Modify: `paper/src/main/resources/config.yml`
- Test: existing `paper/src/test/java/dev/mintychochip/archimedes/config/` (extend)

- [ ] **Step 1: Write failing loader tests**

Add tests (same class/style as existing loader tests):

```java
@Test
void overlappingSailAndEnvelopeMaterialsFailEnable() {
  FileConfiguration yaml = new YamlConfiguration();
  // copy a valid minimal config used by other tests, then:
  yaml.set("envelope-materials", List.of("minecraft:white_wool"));
  IllegalArgumentException error =
      assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(yaml));
  assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("envelope"));
}

@Test
void engineThrustMustBeFiniteNonNegative() {
  FileConfiguration yaml = validConfig();
  yaml.set("engine-thrust", -1);
  assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(yaml));
}
```

Use the suite’s existing `validConfig()` helper if one exists; otherwise duplicate the minimum keys `maximum-blocks`, `target-distance`, etc. from neighboring tests.

- [ ] **Step 2: Run tests to verify fail**

Run: `./gradlew :paper:test --tests '*ShipConfigLoader*'`

Expected: FAIL (unknown keys ignored today, overlap not checked).

- [ ] **Step 3: Implement config fields**

Add to `ShipConfig` (end of the full constructor, with a new overload so existing 19-arg call sites delegate):

```java
private final Set<String> engineMaterials;
private final Set<String> envelopeMaterials;
private final double engineThrust;

public Set<String> engineMaterials() { return engineMaterials; }
public Set<String> envelopeMaterials() { return envelopeMaterials; }
public double engineThrust() { return engineThrust; }
```

19-arg constructor delegates with:

```java
Set.of("minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker"),
Set.of("minecraft:slime_block", "minecraft:honey_block"),
1.0
```

`Set.copyOf` the sets. Reject non-finite or negative `engineThrust` in the full constructor (`IllegalArgumentException`).

Loader keys (top-level, next to `forbidden-materials`):

```yaml
engine-materials:
  - "minecraft:furnace"
  - "minecraft:blast_furnace"
  - "minecraft:smoker"
envelope-materials:
  - "minecraft:slime_block"
  - "minecraft:honey_block"
engine-thrust: 1.0
```

Missing lists → those defaults (same as supplied defaults, not empty). Blank entries dropped; keys lowercased.

After resolving cloth-equivalent sail keys (`endsWith("_wool")` / `_banner` / `_wall_banner`) vs envelope set: if any envelope key is a sail key, throw `IllegalArgumentException` (`envelope-materials cannot include sail cloth`).

- [ ] **Step 4: Run tests**

Run: `./gradlew :paper:test --tests '*Config*'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/dev/mintychochip/archimedes/config/ShipConfig.java \
  paper/src/main/java/dev/mintychochip/archimedes/config/ShipConfigLoader.java \
  paper/src/main/resources/config.yml \
  paper/src/test/java/dev/mintychochip/archimedes/config
git commit -m "feat: add engine and envelope material config"
```

---

### Task 3: Envelope buoyancy from envelope cells only

**Files:**
- Create: `common/src/test/java/dev/mintychochip/archimedes/phys/EnvelopeBuoyancyForceTest.java`
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/EnvelopeBuoyancyForce.java`

- [ ] **Step 1: Write failing apply + step tests**

```java
package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.phys.BodyImpl;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.Transform;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class EnvelopeBuoyancyForceTest {
  @Test
  void liftUsesEnvelopeVolumeOnly() {
    EnvelopeBuoyancyForce force = new EnvelopeBuoyancyForce(2.0, DensityField.uniform(1.2));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 10, List.of(), List.of());
    var world = new UpWorld();
    Force.Result result = force.apply(body, world);
    assertEquals(0, result.force().x(), 1e-9);
    assertEquals(24.0, result.force().y(), 1e-9); // 2 * 1.2 * 10
    assertEquals(0, result.force().z(), 1e-9);
    assertEquals(0, result.torque().length(), 1e-9);
  }

  @Test
  void zeroVolumeIsZeroForce() {
    EnvelopeBuoyancyForce force = new EnvelopeBuoyancyForce(0, DensityField.uniform(1.2));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 10, List.of(), List.of());
    Force.Result result = force.apply(body, new UpWorld());
    assertEquals(0, result.force().length(), 1e-9);
  }

  @Test
  void stepInVacuumProducesNetUp() {
    EnvelopeBuoyancyForce lift = new EnvelopeBuoyancyForce(4.0, DensityField.uniform(1.2));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(0, 40, 0), new Quaterniond()),
            10,
            List.of(),
            List.of(new dev.mintychochip.phys.GravityForce(), lift));
    new PhysicsEngine().step(new UpWorld(), List.of(body));
    assertTrue(body.linearVelocity().y() > 0);
  }

  private static final class UpWorld implements dev.mintychochip.phys.World {
    public org.joml.Vector3dc gravity() { return new Vector3d(0, -10, 0); }
    public dev.mintychochip.phys.FluidField fluidField() {
      return new dev.mintychochip.phys.FluidField() {
        public boolean isFluid(org.joml.Vector3dc point) { return false; }
        public double density(org.joml.Vector3dc point) { return 0; }
      };
    }
    public double timeStep() { return 0.05; }
  }
}
```

Prefer importing `PhysFixtures` if you move the test to `dev.mintychochip.phys`; this class lives in `archimedes.phys` so inline the tiny world.

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.EnvelopeBuoyancyForceTest`

Expected: FAIL (`EnvelopeBuoyancyForce` not found).

- [ ] **Step 3: Implement**

```java
package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.World;
import java.util.Objects;
import org.joml.Vector3d;

/** Aerostatic lift from envelope volume only: {@code F = −ρ V g}. */
public final class EnvelopeBuoyancyForce implements Force {
  private final double volume;
  private final DensityField air;

  public EnvelopeBuoyancyForce(double volume, DensityField air) {
    if (!Double.isFinite(volume) || volume < 0) {
      throw new IllegalArgumentException("volume must be finite and non-negative");
    }
    this.volume = volume;
    this.air = Objects.requireNonNull(air);
  }

  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    double rho = air.density(body.transform().position());
    double mass = rho * volume;
    return new Result(new Vector3d(world.gravity()).mul(-mass), new Vector3d());
  }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.EnvelopeBuoyancyForceTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/archimedes/phys/EnvelopeBuoyancyForce.java \
  common/src/test/java/dev/mintychochip/archimedes/phys/EnvelopeBuoyancyForceTest.java
git commit -m "feat: add envelope-cell aerostatic lift"
```

---

### Task 4: VehicleFactory

**Files:**
- Create: `common/src/test/java/dev/mintychochip/archimedes/phys/VehicleFactoryTest.java`
- Create: `common/src/main/java/dev/mintychochip/archimedes/phys/VehicleFactory.java`
- Delete after wiring in Task 5: `ShipBody.java` (still used until Task 5)

- [ ] **Step 1: Write failing factory tests**

```java
package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.GravityForce;
import dev.mintychochip.phys.MediumThrustForce;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.PressureSailForce;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class VehicleFactoryTest {
  private static final MaterialKeyResolver RESOLVER = block -> {
    String data = block.blockData();
    int bracket = data.indexOf('[');
    return (bracket < 0 ? data : data.substring(0, bracket)).toLowerCase();
  };

  @Test
  void stackedDeckBlocksAddMass() {
    Vehicle small = vehicle(List.of(oak(0, 0, 0)));
    Vehicle stacked = vehicle(List.of(oak(0, 0, 0), oak(0, 1, 0)));
    VehicleFactory factory = factory();
    assertTrue(
        factory.buildBody(stacked, world(), 0, air(), wind(), true).mass()
            > factory.buildBody(small, world(), 0, air(), wind(), true).mass());
  }

  @Test
  void furledSailsKeepMassAndDropSailForce() {
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0), wool(0, 2, 0)));
    vehicle.setSailsEnabled(false);
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    assertEquals(2, body.colliders().size());
    assertFalse(body.forces().stream().anyMatch(PressureSailForce.class::isInstance));
    assertTrue(body.forces().stream().anyMatch(GravityForce.class::isInstance));
  }

  @Test
  void enginesOffKeepMassAndDropThrust() {
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0), furnace(1, 0, 0)));
    vehicle.setEnginesEnabled(false);
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    assertEquals(2, body.colliders().size());
    assertFalse(body.forces().stream().anyMatch(MediumThrustForce.class::isInstance));
  }

  @Test
  void envelopeCellsHoverInEmptyAir() {
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0), slime(0, 3, 0)));
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    assertTrue(body.forces().stream().anyMatch(EnvelopeBuoyancyForce.class::isInstance));
    new PhysicsEngine().step(world(), List.of(body));
    assertTrue(body.linearVelocity().y() > 0);
  }

  @Test
  void hullWithoutEnvelopeDoesNotAttachEnvelopeLift() {
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0)));
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    assertFalse(body.forces().stream().anyMatch(EnvelopeBuoyancyForce.class::isInstance));
  }

  @Test
  void oakPlusEnvelopeDoesNotCountOakAsGasVolume() {
    EnvelopeBuoyancyForce oneCell = new EnvelopeBuoyancyForce(1.0, air());
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0), slime(0, 3, 0)));
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    EnvelopeBuoyancyForce attached =
        (EnvelopeBuoyancyForce)
            body.forces().stream()
                .filter(EnvelopeBuoyancyForce.class::isInstance)
                .findFirst()
                .orElseThrow();
    Force.Result expected = oneCell.apply(body, world());
    Force.Result actual = attached.apply(body, world());
    assertEquals(expected.force().y(), actual.force().y(), 1e-9);
  }

  private static Vehicle vehicle(List<ShipBlock> blocks) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
        blocks,
        new ShipPose(0),
        true);
  }

  private static ShipBlock oak(int x, int y, int z) {
    return new ShipBlock(new BlockPos(x, y, z), "minecraft:oak_planks");
  }

  private static ShipBlock wool(int x, int y, int z) {
    return new ShipBlock(new BlockPos(x, y, z), "minecraft:white_wool[facing=south]");
  }

  private static ShipBlock furnace(int x, int y, int z) {
    return new ShipBlock(new BlockPos(x, y, z), "minecraft:furnace[facing=south]");
  }

  private static ShipBlock slime(int x, int y, int z) {
    return new ShipBlock(new BlockPos(x, y, z), "minecraft:slime_block");
  }

  private static VehicleFactory factory() {
    return new VehicleFactory(RESOLVER, config());
  }

  private static ShipConfig config() {
    return new ShipConfig(
        2048, 8, Set.of(), Set.of(), true, 1, 0.5, 16.0, 10.0, 10.0, 0.5, 0.9,
        Map.of(
            "minecraft:oak_planks", 6.0,
            "minecraft:white_wool", 1.0,
            "minecraft:furnace", 8.0,
            "minecraft:slime_block", 1.0),
        10.0, 80.0, 16.0, 1e-6, 1e-3);
  }

  private static DensityField air() { return DensityField.uniform(1.2); }
  private static FlowField wind() { return FlowField.uniform(new Vector3d(0, 0, 8)); }
  private static dev.mintychochip.phys.World world() {
    return new EnvelopeBuoyancyForceTest.UpWorld(); // if package-private, duplicate the World stub here
  }
}
```

If `UpWorld` is private, copy the same `World` stub into this test. If the 19-arg `ShipConfig` already injects default engine/envelope sets, the furnace/slime tests work without the 22-arg constructor. If you added a 22-arg constructor, pass the default sets explicitly.

Make `EnvelopeBuoyancyForce` volume accessible for the oak-vs-gas assertion: either a package-private `volume()` accessor or compare apply results as shown (preferred — no extra API).

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.VehicleFactoryTest`

Expected: FAIL (`VehicleFactory` not found).

- [ ] **Step 3: Implement factory**

```java
public final class VehicleFactory {
  private final MaterialKeyResolver resolver;
  private final ShipConfig config;

  public VehicleFactory(MaterialKeyResolver resolver, ShipConfig config) {
    this.resolver = Objects.requireNonNull(resolver);
    this.config = Objects.requireNonNull(config);
  }

  public Body buildBody(
      Vehicle vehicle,
      World world,
      int riders,
      DensityField air,
      FlowField wind,
      boolean withActuators) {
    List<Force> forces = new ArrayList<>();
    if (vehicle.buoyancyEnabled()) {
      forces.add(new GravityForce());
      forces.add(new ShipBuoyancyForce());
      forces.add(new VegetationDragForce(0.8));
      forces.add(new QuadraticDragForce(0.8, DensityField.liquid(world.fluidField())));
      double envelopeVolume = 0;
      for (ShipBlock block : vehicle.blocks()) {
        if (config.envelopeMaterials().contains(resolver.key(block))) {
          envelopeVolume += 1.0;
        }
      }
      if (envelopeVolume > 0) {
        forces.add(new EnvelopeBuoyancyForce(envelopeVolume, air));
      }
      if (withActuators && vehicle.sailsEnabled()) {
        forces.add(new QuadraticDragForce(0.05));
        forces.addAll(
            ShipSails.forces(vehicle, resolver, clothKeys(vehicle), air, wind));
      }
      if (withActuators && vehicle.enginesEnabled()) {
        DensityField medium =
            point ->
                world.fluidField().isFluid(point)
                    ? world.fluidField().density(point)
                    : air.density(point);
        for (ShipBlock block : vehicle.blocks()) {
          if (!config.engineMaterials().contains(resolver.key(block))) {
            continue;
          }
          Vector3d point =
              new Vector3d(block.pos().x() + 0.5, block.pos().y() + 0.5, block.pos().z() + 0.5);
          forces.add(
              new MediumThrustForce(point, facingNormal(block.blockData()), config.engineThrust(), medium));
        }
      }
    }
    return ShipBody.from(
        vehicle, resolver, config, riders, forces.toArray(Force[]::new));
  }
}
```

Reuse `ShipSails.facingNormal` (package-private it, or duplicate the small facing parser already in `ShipSails`). Change `ShipSails.forces` and `ShipBody.from` first parameters from `Ship` to `Vehicle` (already done in Task 1 if they took `Ship`).

`clothKeys` copies `ShipPhysicsImpl.isCloth`.

`buildBody` must still attach colliders for **all** blocks via `ShipBody.from` even when flags are off.

- [ ] **Step 4: Run tests**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.VehicleFactoryTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/archimedes/phys/VehicleFactory.java \
  common/src/test/java/dev/mintychochip/archimedes/phys/VehicleFactoryTest.java \
  common/src/main/java/dev/mintychochip/archimedes/phys/ShipSails.java
git commit -m "feat: build vehicle bodies from a factory"
```

---

### Task 5: Wire physics tick through the factory

**Files:**
- Modify: `common/src/main/java/dev/mintychochip/archimedes/phys/ShipPhysicsImpl.java`
- Modify: `common/src/test/java/dev/mintychochip/archimedes/phys/ShipPhysicsTest.java` (keep green; add one envelope tick if cheap)
- Delete: `common/src/main/java/dev/mintychochip/archimedes/phys/ShipBody.java` only if `VehicleFactory` inlined collider construction; otherwise keep `ShipBody.from` as a helper called by the factory.

- [ ] **Step 1: Write a failing physics test that a slime envelope rises in vacuum**

```java
@Test
void envelopeVehicleRisesWhenDry() {
  Vehicle vehicle =
      new Vehicle(
          UUID.randomUUID(),
          UUID.randomUUID(),
          new ShipOrigin(worldId, 0, 80, 0),
          List.of(
              new ShipBlock(new BlockPos(0, 0, 0), "minecraft:slime_block")),
          new ShipPose(0),
          true);
  // world fluidField always dry; air DensityField.uniform(1.2)
  ShipPhysics physics = newPhysics(/* dry world */);
  assertTrue(physics.tick(vehicle));
  assertTrue(vehicle.pose().y() > 0);
}
```

Fit this into `ShipPhysicsTest`’s existing fakes (copy the smallest dry-world setup already used for sail XZ). If tick returns false because path is blocked, use a clear-air fake `WorldMutator`.

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.ShipPhysicsTest.envelopeVehicleRisesWhenDry`

Expected: FAIL (current `body()` never attaches envelope lift; dry hull falls or does not rise).

- [ ] **Step 3: Replace `body(Ship, boolean)` with factory**

In `ShipPhysicsImpl`:

```java
private final VehicleFactory factory;

// in constructors, after assigning resolver/config:
this.factory = new VehicleFactory(resolver, config);

private Body body(Vehicle vehicle, boolean withActuators) {
  return factory.buildBody(
      vehicle, world, riderCount.count(vehicle), air, wind, withActuators);
}
```

Delete the local force-list assembly (`GravityForce`, `ShipSails`, …). Keep chunk gating, integrate, clamp, `moveDirect`. `rise` still calls `body(vehicle, false)` so engines/sails are off during settle.

- [ ] **Step 4: Run tests**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.phys.*`

Expected: PASS, including A1–A20 / acceptance tests.

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/dev/mintychochip/archimedes/phys/ShipPhysicsImpl.java \
  common/src/test/java/dev/mintychochip/archimedes/phys
git commit -m "feat: step vehicles through VehicleFactory"
```

---

### Task 6: `/arch sails` and `/arch engines`

**Files:**
- Modify: `api/src/main/java/dev/mintychochip/archimedes/ship/ShipService.java`
- Modify: `common/src/main/java/dev/mintychochip/archimedes/ship/ShipServiceImpl.java`
- Modify: `common/src/test/java/dev/mintychochip/archimedes/ship/ShipServiceImplTest.java`
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/command/ShipCommand.java`
- Modify: `paper/src/main/java/dev/mintychochip/archimedes/command/ShipTabCompleter.java`
- Modify: `paper/src/test/java/dev/mintychochip/archimedes/command/ShipCommandTest.java`
- Modify: `paper/src/main/resources/plugin.yml`

- [ ] **Step 1: Write failing service + command tests**

Service:

```java
@Test
void toggleSailsFlipsFlagOnNearbyVehicle() {
  // assemble or register a vehicle with cloth, then:
  assertTrue(service.toggleSails(vehicle.id(), OWNER, false));
  assertFalse(service.all().iterator().next().sailsEnabled());
  assertTrue(service.toggleSails(vehicle.id(), OWNER, false));
  assertTrue(service.all().iterator().next().sailsEnabled());
}
```

Targeting: follow inspect — owner or operator, nearby hull. Reuse the helper that puts a vehicle in the registry.

Command:

```java
@Test
void sailsSubcommandRequiresPermissionAndDelegates() {
  // Fake service records toggleSails; player with archimedes.sails
  assertTrue(command.onCommand(player, bukkitCommand, "arch", new String[] {"sails"}));
  assertTrue(service.calls.contains("toggleSails"));
}
```

Mirror the existing inspect targeting test for “No ship nearby.”

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.ship.ShipServiceImplTest.toggleSailsFlipsFlagOnNearbyVehicle`

Expected: FAIL (`toggleSails` missing).

- [ ] **Step 3: Implement**

`ShipService`:

```java
boolean toggleSails(UUID vehicleId, UUID requesterId, boolean operator);
boolean toggleEngines(UUID vehicleId, UUID requesterId, boolean operator);
```

Implementation: same ownership check as disassemble (owner or operator). Flip the flag. Do not save (in-memory only). Return false + `lastError` when missing/unauthorized.

`ShipCommand`: nearby hull via `ShipTargeting.nearest` (same as inspect). Subcommands `sails` and `engines`. Usage line adds them. Messages: `Sails furled.` / `Sails set.` and `Engines off.` / `Engines on.`

`plugin.yml`:

```yaml
usage: /arch assemble|inspect|disassemble|kill|buoyancy|sink|sail|sails|engines
```

```yaml
archimedes.sails:
  description: Toggle vehicle sails
  default: true
archimedes.engines:
  description: Toggle vehicle engines
  default: true
```

Tab completer `SUBCOMMANDS` adds `"sails"`, `"engines"`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :common:test --tests dev.mintychochip.archimedes.ship.ShipServiceImplTest :paper:test --tests dev.mintychochip.archimedes.command.*`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/dev/mintychochip/archimedes/ship/ShipService.java \
  common/src/main/java/dev/mintychochip/archimedes/ship/ShipServiceImpl.java \
  common/src/test/java/dev/mintychochip/archimedes/ship/ShipServiceImplTest.java \
  paper/src/main/java/dev/mintychochip/archimedes/command \
  paper/src/test/java/dev/mintychochip/archimedes/command \
  paper/src/main/resources/plugin.yml
git commit -m "feat: toggle sails and engines with /arch"
```

---

### Task 7: Living specs

**Files:**
- Modify: `docs/specs/ship-model.md`
- Modify: `docs/specs/physics.md`
- Modify: `docs/specs/commands.md`
- Modify: `docs/specs/buoyancy.md`
- Modify: `docs/specs/README.md`

- [ ] **Step 1: Update catalogs to match shipped behavior**

`ship-model.md`:

- Intent: `Vehicle` is the domain object (physics aggregate). `ShipBlock` / pose / origin remain hull snapshots.
- Current: check off Vehicle rename, in-memory sail/engine flags, all captured blocks add mass.
- Decisions log: 2026-08-19 rows from the design spec.
- Last updated: 2026-08-19.

`physics.md`:

- Ship client uses `VehicleFactory`; envelope lift is intrinsic and envelope-cell-only.
- Next: remaining horizontal steering / yaw still future.
- Current: check factory, envelope, medium-thrust engines.

`commands.md`:

- `/arch sails`, `/arch engines`, permissions. `/arch` is the command.

`buoyancy.md`:

- Buoyancy remains intrinsic waterline lift; `/arch buoyancy` is a physics kill switch, not a kind.

`README.md`:

- Index row: ship-model covers Vehicle data; commands surface is `/arch`.

- [ ] **Step 2: Commit**

```bash
git add docs/specs
git commit -m "docs: record Vehicle factory in living specs"
```

---

## Self-review (plan vs spec)

| Spec item | Task |
|-----------|------|
| `Vehicle` is the only aggregate type | Task 1 |
| Factory rebuilds `Body` each tick | Tasks 4–5 |
| All captured blocks add mass | Task 4 tests |
| Intrinsic waterline + envelope (envelope cells only) | Tasks 3–5 |
| Sails/engines flags gate thrust only | Tasks 1, 4, 6 |
| Turbines = engines = `MediumThrustForce` | Task 4 |
| `/arch` not `/vehicle` | Task 6 |
| Flags not persisted | Tasks 1, 6 |
| Envelope materials disjoint from cloth | Task 2 |
| Tests through `Physics.step` | Tasks 3–5 |
| Full `ShipPhysics`/`ShipRuntime` filename rename | Deferred (APIs already take `Vehicle`) |

---

## Notes for implementers

- Land MediumThrust catalog work first if `MediumThrustForce` is still untracked.
- Do not “fix” pre-existing Checkstyle in `WaterlineResolver` or PMD in `ShipPhysicsTest`.
- `VehicleFactory.buildBody(..., withActuators=false)` is the rise path (no sails/engines).
- Engine medium: liquid density when `isFluid`, otherwise air — one law, both media.
