package dev.jlo.ships;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jlo.ships.ship.ShipRuntimeException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipsPluginTest {
  @Test
  void cleanupCoordinatorAttemptsBothActionsAndLogsBothFailures() {
    List<String> calls = new ArrayList<>();
    List<String> logs = new ArrayList<>();
    ShipsPlugin.CleanupCoordinator.run(
        () -> {
          calls.add("registered");
          throw new IllegalStateException("registered failure");
        },
        () -> {
          calls.add("tagged");
          throw new IllegalStateException("tagged failure");
        },
        logs::add);

    assertEquals(List.of("registered", "tagged"), calls);
    assertEquals(
        List.of(
            "Failed to remove registered ship runtime: registered failure",
            "Failed to remove tagged ship runtime: tagged failure"),
        logs);
  }

  @Test
  void loadFailureHelperHandlesContractRuntimeException() {
    List<String> logs = new ArrayList<>();
    List<String> disabled = new ArrayList<>();
    ShipsPlugin.CleanupCoordinator.handleLoadFailure(
        new ShipRuntimeException(new IllegalStateException("runtime failure")),
        logs::add,
        () -> disabled.add("disabled"));
    assertEquals(
        List.of("Failed to load ships: java.lang.IllegalStateException: runtime failure"), logs);
    assertEquals(List.of("disabled"), disabled);
  }
}
