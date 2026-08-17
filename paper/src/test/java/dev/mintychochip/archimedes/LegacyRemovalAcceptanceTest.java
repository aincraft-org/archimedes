package dev.mintychochip.archimedes;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LegacyRemovalAcceptanceTest {
  @Test
  void approvedPackagesArePresent() throws Exception {
    assertNotNull(Class.forName("dev.mintychochip.phys.Bounds"));
    assertNotNull(Class.forName("dev.mintychochip.phys.Aabb"));
    assertNotNull(Class.forName("dev.mintychochip.archimedes.phys.ShipPhysics"));
    assertThrows(
        ClassNotFoundException.class, () -> Class.forName("dev.mintychochip.phys.BuoyancyImpl"));
  }
}
