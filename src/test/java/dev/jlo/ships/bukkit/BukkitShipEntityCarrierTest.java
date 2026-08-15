package dev.jlo.ships.bukkit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Compile-level coverage for the Bukkit ship entity carrier. */
class BukkitShipEntityCarrierTest {
  @Test
  void carrierCompilesAgainstConfiguredPaperApi() {
    assertTrue(BukkitShipEntityCarrier.class.getName().contains("ShipEntityCarrier"));
  }
}
