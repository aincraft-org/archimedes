package dev.jlo.ships.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import dev.jlo.ships.model.ShipPose;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Persistence round-trip tests for the JSON ship store. */
class ShipStoreTest {
  @TempDir Path tempDir;

  @Test
  void savesAndLoadsShipsWithExactData() throws Exception {
    UUID shipId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    Ship ship =
        new Ship(
            shipId,
            owner,
            new ShipOrigin(world, 100, 200, 300),
            List.of(
                new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks"),
                new ShipBlock(new BlockPos(3, -2, 7), "minecraft:stone[waterlogged=true]")));
    Map<UUID, Ship> ships = new LinkedHashMap<>();
    ships.put(shipId, ship);

    ShipStore store = new ShipStore(tempDir);
    store.saveAll(ships);

    Map<UUID, Ship> loaded = store.loadAll();
    assertEquals(1, loaded.size());
    Ship restored = loaded.get(shipId);
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
    Ship oldShip =
        new Ship(
            UUID.randomUUID(),
            owner,
            new ShipOrigin(world, 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    Map<UUID, Ship> first = new LinkedHashMap<>();
    first.put(oldShip.id(), oldShip);
    ShipStore store = new ShipStore(tempDir);
    store.saveAll(first);

    Ship newShip =
        new Ship(
            UUID.randomUUID(),
            owner,
            new ShipOrigin(world, 5, 6, 7),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:dirt")));
    Map<UUID, Ship> second = new LinkedHashMap<>();
    second.put(newShip.id(), newShip);
    store.saveAll(second);

    Map<UUID, Ship> loaded = store.loadAll();
    assertEquals(1, loaded.size());
    assertTrue(loaded.containsKey(newShip.id()));
  }

  @Test
  void restoresThroughTemporaryFile() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID world = UUID.randomUUID();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            owner,
            new ShipOrigin(world, 1, 2, 3),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    Map<UUID, Ship> ships = new LinkedHashMap<>();
    ships.put(ship.id(), ship);
    ShipStore store = new ShipStore(tempDir);
    store.saveAll(ships);

    Map<UUID, Ship> loaded = store.loadAll();
    assertEquals(ship.id(), loaded.keySet().iterator().next());
  }

  @Test
  void persistsPoseAndBuoyancyFlag() throws Exception {
    UUID shipId = UUID.randomUUID();
    Ship ship =
        new Ship(
            shipId,
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 1, 2, 3),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks")),
            new ShipPose(12.5),
            false);
    Map<UUID, Ship> ships = new LinkedHashMap<>();
    ships.put(shipId, ship);

    ShipStore store = new ShipStore(tempDir);
    store.saveAll(ships);

    Ship restored = store.loadAll().get(shipId);
    assertEquals(12.5, restored.pose().y());
    assertFalse(restored.buoyancyEnabled());
  }

  @Test
  void loadsLegacyFileWithoutPose() throws Exception {
    Path file = tempDir.resolve("ships.json");
    Files.writeString(
        file,
        "[{\"id\":\"00000000-0000-0000-0000-000000000001\","
            + "\"owner\":\"00000000-0000-0000-0000-000000000002\","
            + "\"origin\":{\"world\":\"00000000-0000-0000-0000-000000000003\",\"x\":1,\"y\":2,\"z\":3},"
            + "\"blocks\":[{\"pos\":{\"x\":0,\"y\":0,\"z\":0},\"data\":\"minecraft:stone\"}]}]");
    ShipStore store = new ShipStore(tempDir);
    Ship restored = store.loadAll().values().iterator().next();
    assertEquals(0.0, restored.pose().y());
    assertTrue(restored.buoyancyEnabled());
  }
}
