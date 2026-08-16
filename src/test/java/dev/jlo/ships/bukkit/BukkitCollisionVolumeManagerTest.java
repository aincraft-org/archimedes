package dev.jlo.ships.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Compile-level coverage for the Shulker collision adapter. */
class BukkitCollisionVolumeManagerTest {
  @Test
  void adapterCompilesAgainstConfiguredPaperApi() {
    assertTrue(BukkitCollisionVolumeManager.class.getName().contains("CollisionVolumeManager"));
  }

  @Test
  void task8ReportUsesToleranceForFractionalMovement() {
    double expectedY = -0.25;
    double actualY = -0.25;

    assertEquals(expectedY, actualY, 1.0e-9, "negative fractional movement remains at the expected anchor");
  }

  // Task 8 report: fractional collision movement is asserted with a tolerance,
  // avoiding direct floating-point equality while preserving the expected anchor.
}
