package dev.mintychochip.archimedes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.ship.ShipRuntimeException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchimedesPluginTest {
  private static final String REGISTERED = "registered";

  @Test
  void cleanupCoordinatorAttemptsBothActionsAndLogsBothFailures() {
    List<String> calls = new ArrayList<>();
    List<String> logs = new ArrayList<>();
    ArchimedesPlugin.CleanupCoordinator.run(
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
    ArchimedesPlugin.CleanupCoordinator.handleLoadFailure(
        new ShipRuntimeException(new IllegalStateException("runtime failure")),
        logs::add,
        () -> disabled.add("disabled"));
    assertEquals(
        List.of("Failed to load Archimedes: java.lang.IllegalStateException: runtime failure"),
        logs);
    assertEquals(List.of("disabled"), disabled);
  }

  @Test
  void registrationHelperRunsOnlyAfterSuccessfulLoad() {
    List<String> events = new ArrayList<>();
    ArchimedesPlugin.registerAfterLoad(() -> events.add("loaded"), () -> events.add(REGISTERED));
    assertEquals(List.of("loaded", REGISTERED), events);
  }

  @Test
  void registrationHelperDoesNotRunAfterLoadFailure() {
    List<String> events = new ArrayList<>();
    try {
      ArchimedesPlugin.registerAfterLoad(
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

  @Test
  void pluginYmlNamesArchimedesPluginAndPaperApiVersion() throws Exception {
    try (InputStream in =
        ArchimedesPlugin.class.getClassLoader().getResourceAsStream("plugin.yml")) {
      assertNotNull(in);
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(text.contains("name: Archimedes"));
      assertTrue(text.contains("main: dev.mintychochip.archimedes.ArchimedesPlugin"));
      assertTrue(text.contains("api-version: '26.2'"));
    }
  }
}
