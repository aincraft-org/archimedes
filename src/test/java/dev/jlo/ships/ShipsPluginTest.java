package dev.jlo.ships;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jlo.ships.ship.ShipRuntimeException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipsPluginTest {
  private static final String REGISTERED = "registered";

  @Test
  void cleanupCoordinatorAttemptsBothActionsAndLogsBothFailures() {
    List<String> calls = new ArrayList<>();
    List<String> logs = new ArrayList<>();
    ShipsPlugin.CleanupCoordinator.run(
        () -> {
          calls.add(REGISTERED);
          throw new IllegalStateException("registered failure");
        },
        () -> {
          calls.add("tagged");
          throw new IllegalStateException("tagged failure");
        },
        logs::add);

    assertEquals(List.of(REGISTERED, "tagged"), calls);
    assertEquals(
        List.of(
            "Failed to remove " + REGISTERED + " ship runtime: registered failure",
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

  @Test
  void registrationHelperRunsOnlyAfterSuccessfulLoad() {
    List<String> events = new ArrayList<>();
    ShipsPlugin.registerAfterLoad(() -> events.add("loaded"), () -> events.add(REGISTERED));
    assertEquals(List.of("loaded", REGISTERED), events);
  }

  @Test
  void registrationHelperDoesNotRunAfterLoadFailure() {
    List<String> events = new ArrayList<>();
    try {
      ShipsPlugin.registerAfterLoad(
          () -> {
            events.add("load-failed");
            throw new IllegalStateException("load failure");
          },
          () -> events.add(REGISTERED));
    } catch (IllegalStateException expected) {
      // Expected: registration must not run after a failed load.
    }
    assertEquals(List.of("load-failed"), events);
  }
}
