package dev.mintychochip.archimedes.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ShipStoreTest {
  @TempDir Path tempDir;

  @Test
  void savesAndLoadsShipsWithExactData() throws Exception {
    UUID shipId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    Vehicle ship =
        new Vehicle(
            shipId,
            owner,
            new ShipOrigin(world, 100, 200, 300),
            List.of(
                new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks"),
                new ShipBlock(new BlockPos(3, -2, 7), "minecraft:stone[waterlogged=true]")));
    Map<UUID, Vehicle> ships = new LinkedHashMap<>();
    ships.put(shipId, ship);

    ShipStore store = new ShipStore(tempDir);
    store.saveAll(ships);

    assertTrue(Files.exists(tempDir.resolve("archimedes.json")));
    assertFalse(Files.exists(tempDir.resolve("ships.json")));
    Map<UUID, Vehicle> loaded = store.loadAll();
    assertEquals(1, loaded.size());
    Vehicle restored = loaded.get(shipId);
    assertEquals(owner, restored.ownerId());
    assertEquals(world, restored.origin().worldId());
    assertEquals(100, restored.origin().x());
    assertEquals(200, restored.origin().y());
    assertEquals(300, restored.origin().z());
    assertEquals(2, restored.blocks().size());
    assertTrue(
        restored.blocks().stream()
            .anyMatch(
                b ->
                    b.blockData().equals("minecraft:oak_planks")
                        && b.pos().x() == 0
                        && b.pos().y() == 0
                        && b.pos().z() == 0));
    assertTrue(
        restored.blocks().stream()
            .anyMatch(
                b ->
                    b.blockData().equals("minecraft:stone[waterlogged=true]")
                        && b.pos().x() == 3
                        && b.pos().y() == -2
                        && b.pos().z() == 7));
  }

  @Test
  void loadAllReturnsEmptyWhenMissingFile() throws Exception {
    ShipStore store = new ShipStore(tempDir);
    assertTrue(store.loadAll().isEmpty());
  }

  @Test
  void overwritesPreviousFile() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    Vehicle oldShip =
        new Vehicle(
            UUID.randomUUID(),
            owner,
            new ShipOrigin(world, 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    Map<UUID, Vehicle> first = new LinkedHashMap<>();
    first.put(oldShip.id(), oldShip);
    ShipStore store = new ShipStore(tempDir);
    store.saveAll(first);

    Vehicle newShip =
        new Vehicle(
            UUID.randomUUID(),
            owner,
            new ShipOrigin(world, 5, 6, 7),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:dirt")));
    Map<UUID, Vehicle> second = new LinkedHashMap<>();
    second.put(newShip.id(), newShip);
    store.saveAll(second);

    Map<UUID, Vehicle> loaded = store.loadAll();
    assertEquals(1, loaded.size());
    assertTrue(loaded.containsKey(newShip.id()));
  }

  @Test
  void restoresThroughTemporaryFile() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            owner,
            new ShipOrigin(world, 1, 2, 3),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    Map<UUID, Vehicle> ships = new LinkedHashMap<>();
    ships.put(ship.id(), ship);
    ShipStore store = new ShipStore(tempDir);
    store.saveAll(ships);

    Map<UUID, Vehicle> loaded = store.loadAll();
    assertEquals(ship.id(), loaded.keySet().iterator().next());
  }

  @Test
  void persistsPoseAndBuoyancyFlag() throws Exception {
    UUID shipId = UUID.randomUUID();
    Vehicle ship =
        new Vehicle(
            shipId,
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 1, 2, 3),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks")),
            new ShipPose(12.5),
            false);
    Map<UUID, Vehicle> ships = new LinkedHashMap<>();
    ships.put(shipId, ship);

    ShipStore store = new ShipStore(tempDir);
    store.saveAll(ships);

    Vehicle restored = store.loadAll().get(shipId);
    assertEquals(12.5, restored.pose().y());
    assertFalse(restored.buoyancyEnabled());
  }

  @Test
  void a19LegacyFileWithoutPoseDefaultsToZeroAndEnabled() throws Exception {
    Path file = tempDir.resolve("ships.json");
    Files.writeString(
        file,
        "[{\"id\":\"00000000-0000-0000-0000-000000000001\","
            + "\"owner\":\"00000000-0000-0000-0000-000000000002\","
            + "\"origin\":{\"world\":\"00000000-0000-0000-0000-000000000003\","
            + "\"x\":1,\"y\":2,\"z\":3},"
            + "\"blocks\":[{\"pos\":{\"x\":0,\"y\":0,\"z\":0},\"data\":\"minecraft:stone\"}]}]");
    ShipStore store = new ShipStore(tempDir);
    Vehicle restored = store.loadAll().values().iterator().next();
    assertEquals(0.0, restored.pose().y());
    assertTrue(restored.buoyancyEnabled());
  }

  @Test
  void a15RestartRestoresFloatedPoseAndRecomputesMassWithZeroRiders() throws Exception {
    UUID shipId = UUID.randomUUID();
    Vehicle ship =
        new Vehicle(
            shipId,
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")),
            new ShipPose(7.5),
            true);
    Map<UUID, Vehicle> ships = new LinkedHashMap<>();
    ships.put(shipId, ship);

    ShipStore store = new ShipStore(tempDir);
    store.saveAll(ships);
    Vehicle restored = store.loadAll().get(shipId);

    assertEquals(7.5, restored.pose().y(), 1e-9);

    dev.mintychochip.archimedes.config.ShipConfig config =
        new dev.mintychochip.archimedes.config.ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            32.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("minecraft:stone", 2.0),
            1.0,
            80.0,
            32.0,
            1e-6,
            1e-3);
    double mass =
        dev.mintychochip.archimedes.phys.ShipMassModel.mass(
            restored, b -> b.blockData(), config, 0);
    assertEquals(2.0, mass, 1e-9);
  }
}
