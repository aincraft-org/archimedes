package dev.mintychochip.archimedes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
