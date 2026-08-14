package dev.jlo.ships.bukkit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Compile-level coverage for the Shulker collision adapter. */
class BukkitCollisionVolumeManagerTest {
  @Test
  void adapterCompilesAgainstConfiguredPaperApi() {
    assertTrue(BukkitCollisionVolumeManager.class.getName().contains("CollisionVolumeManager"));
  }
}
