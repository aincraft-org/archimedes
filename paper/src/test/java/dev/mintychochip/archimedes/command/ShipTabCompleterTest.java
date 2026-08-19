package dev.mintychochip.archimedes.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class ShipTabCompleterTest {
  private static final String SHIP_COMMAND = "ship";
  private static final Command COMMAND =
      new Command(SHIP_COMMAND) {
        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
          return true;
        }
      };
  private static final CommandSender SENDER =
      (CommandSender)
          java.lang.reflect.Proxy.newProxyInstance(
              ShipTabCompleterTest.class.getClassLoader(),
              new Class<?>[] {CommandSender.class},
              (proxy, method, args) -> method.getName().equals("name") ? "tester" : null);

  @Test
  void filtersFirstArgumentCaseInsensitively() {
    assertEquals(
        List.of("assemble"),
        new ShipTabCompleter().onTabComplete(SENDER, COMMAND, SHIP_COMMAND, new String[] {"AsS"}));
  }

  @Test
  void returnsAllSubcommandsForEmptyPrefix() {
    assertEquals(
        List.of("assemble", "inspect", "disassemble", "kill", "buoyancy", "sink", "sail"),
        new ShipTabCompleter().onTabComplete(SENDER, COMMAND, SHIP_COMMAND, new String[] {""}));
  }

  @Test
  void completesKillAll() {
    assertEquals(
        List.of("all"),
        new ShipTabCompleter()
            .onTabComplete(SENDER, COMMAND, SHIP_COMMAND, new String[] {"kill", "a"}));
  }

  @Test
  void returnsNoCompletionForLaterArguments() {
    assertEquals(
        List.of(),
        new ShipTabCompleter()
            .onTabComplete(SENDER, COMMAND, SHIP_COMMAND, new String[] {"sink", "1"}));
  }

  @Test
  void completesSailSizes() {
    assertEquals(
        List.of("small"),
        new ShipTabCompleter()
            .onTabComplete(SENDER, COMMAND, SHIP_COMMAND, new String[] {"sail", "s"}));
  }

  @Test
  void completesSailMeshOption() {
    assertEquals(
        List.of("mesh"),
        new ShipTabCompleter()
            .onTabComplete(SENDER, COMMAND, SHIP_COMMAND, new String[] {"sail", "mes"}));
    assertEquals(
        List.of("mesh"),
        new ShipTabCompleter()
            .onTabComplete(SENDER, COMMAND, SHIP_COMMAND, new String[] {"sail", "large", "m"}));
  }
}
