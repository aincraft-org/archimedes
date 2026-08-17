package dev.mintychochip.archimedes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for the ship pose and buoyancy flag. */
class ShipPoseTest {
  @Test
  void anchorFloorsFractionalY() {
    assertEquals(0, new ShipPose(0.5).anchorDy());
    assertEquals(1, new ShipPose(1.0).anchorDy());
    assertEquals(2, new ShipPose(2.9).anchorDy());
    assertEquals(-1, new ShipPose(-0.1).anchorDy());
  }

  @Test
  void defaultShipHasZeroPoseAndBuoyancyEnabled() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    assertEquals(0.0, ship.pose().y());
    assertTrue(ship.buoyancyEnabled());
  }

  @Test
  void poseAndBuoyancyAreMutable() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
    ship.setPose(new ShipPose(3.5));
    ship.setBuoyancyEnabled(false);
    assertEquals(3.5, ship.pose().y());
    assertEquals(3, ship.pose().anchorDy());
    assertFalse(ship.buoyancyEnabled());
  }
}
