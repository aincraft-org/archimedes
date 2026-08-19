package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SailRiggingTest {
  private static final String OAK = "minecraft:oak_planks";
  private static final String WOOL = "minecraft:white_wool";

  @Test
  void clothAgainstAMastHasDistanceZero() {
    Vehicle ship =
        vehicle(
            new ShipBlock(new BlockPos(0, 0, 0), OAK), new ShipBlock(new BlockPos(0, 1, 0), WOOL));
    assertEquals(0, SailRigging.distanceToRigid(ship, new BlockPos(0, 1, 0)));
  }

  @Test
  void clothStackedAwayFromTheHullIncreasesDistance() {
    Vehicle ship =
        vehicle(
            new ShipBlock(new BlockPos(0, 0, 0), OAK),
            new ShipBlock(new BlockPos(0, 1, 0), WOOL),
            new ShipBlock(new BlockPos(0, 2, 0), WOOL),
            new ShipBlock(new BlockPos(0, 3, 0), WOOL));
    assertEquals(0, SailRigging.distanceToRigid(ship, new BlockPos(0, 1, 0)));
    assertEquals(1, SailRigging.distanceToRigid(ship, new BlockPos(0, 2, 0)));
    assertEquals(2, SailRigging.distanceToRigid(ship, new BlockPos(0, 3, 0)));
  }

  @Test
  void clothWithNoRigidNeighborIsUnsupported() {
    Vehicle ship = vehicle(new ShipBlock(new BlockPos(0, 0, 0), WOOL));
    assertEquals(Integer.MAX_VALUE, SailRigging.distanceToRigid(ship, new BlockPos(0, 0, 0)));
  }

  @Test
  void unsupportedClothFailsADefaultWindLoad() {
    assertTrue(SailRigging.fails(60, Integer.MAX_VALUE, 100, true));
    assertFalse(SailRigging.fails(60, Integer.MAX_VALUE, 100, false));
    assertFalse(SailRigging.fails(60, 0, 100));
    assertTrue(SailRigging.fails(60, 1, 100));
  }

  @Test
  void tornClothDoesNotCarryTheLoadPath() {
    Vehicle ship =
        vehicle(
            new ShipBlock(new BlockPos(0, 0, 0), OAK),
            new ShipBlock(new BlockPos(0, 1, 0), WOOL),
            new ShipBlock(new BlockPos(0, 2, 0), WOOL));
    ship.tearCloth(new BlockPos(0, 1, 0));
    assertEquals(Integer.MAX_VALUE, SailRigging.distanceToRigid(ship, new BlockPos(0, 2, 0)));
  }

  private static Vehicle vehicle(ShipBlock... blocks) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(blocks));
  }
}
