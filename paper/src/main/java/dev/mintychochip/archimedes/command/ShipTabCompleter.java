package dev.mintychochip.archimedes.command;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Tab completion for {@code /arch}. */
public final class ShipTabCompleter implements org.bukkit.command.TabCompleter {
  /** Known ship subcommands. */
  private static final List<String> SUBCOMMANDS =
      List.of(
          "assemble",
          "inspect",
          "disassemble",
          "kill",
          "buoyancy",
          "sink",
          "sail",
          "turn",
          "collision");

  /** Kill argument names. */
  private static final List<String> KILL_ARGS = List.of("all");

  /** Collision mode tokens. */
  private static final List<String> COLLISION_MODES = List.of("a", "b", "full", "streamed");

  /** Sail size names, including the 3D mesh token. */
  private static final List<String> SAIL_SIZES = List.of("small", "medium", "large", "mesh");

  /** Extra sail argument: 3D cloth volume. */
  private static final List<String> SAIL_SHAPES = List.of("mesh");

  /** Cardinal cloth headings for {@code /arch sail}. */
  private static final List<String> SAIL_FACINGS = List.of("north", "south", "east", "west");

  /** Cardinal headings and relative turns for {@code /arch turn}. */
  private static final List<String> TURN_FACINGS =
      List.of("north", "south", "east", "west", "left", "right");

  /**
   * Completes the first {@code /arch} argument from the known subcommands.
   *
   * <p>The first argument is the subcommand list; {@code sail}, {@code turn}, and {@code kill} also
   * complete later arguments. {@code sail} completes size, {@code mesh}, and facing; later sail
   * arguments complete remaining shape and facing tokens.
   *
   * @param sender command sender
   * @param command invoked command
   * @param alias label used to invoke the command
   * @param args arguments entered so far
   * @return matching subcommands for the first argument, or an empty list
   */
  @Override
  public List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
      return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(prefix)).toList();
    }
    if (args.length == 2 && "sail".equalsIgnoreCase(args[0])) {
      String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
      return completeSail(prefix, true, true);
    }
    if (args.length == 3 && "sail".equalsIgnoreCase(args[0])) {
      String prefix = args[2].toLowerCase(java.util.Locale.ROOT);
      return completeSail(prefix, false, true);
    }
    if (args.length == 4 && "sail".equalsIgnoreCase(args[0])) {
      String prefix = args[3].toLowerCase(java.util.Locale.ROOT);
      return completeSail(prefix, false, false);
    }
    if (args.length == 2 && "turn".equalsIgnoreCase(args[0])) {
      String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
      return TURN_FACINGS.stream().filter(face -> face.startsWith(prefix)).toList();
    }
    if (args.length == 2 && "kill".equalsIgnoreCase(args[0])) {
      String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
      return KILL_ARGS.stream().filter(arg -> arg.startsWith(prefix)).toList();
    }
    if (args.length == 2 && "collision".equalsIgnoreCase(args[0])) {
      String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
      return COLLISION_MODES.stream().filter(arg -> arg.startsWith(prefix)).toList();
    }
    return List.of();
  }

  private static List<String> completeSail(String prefix, boolean sizes, boolean shapes) {
    List<String> matches = new java.util.ArrayList<>();
    if (sizes) {
      for (String option : SAIL_SIZES) {
        if (option.startsWith(prefix)) {
          matches.add(option);
        }
      }
    }
    if (shapes) {
      for (String option : SAIL_SHAPES) {
        if (option.startsWith(prefix) && !matches.contains(option)) {
          matches.add(option);
        }
      }
    }
    for (String option : SAIL_FACINGS) {
      if (option.startsWith(prefix)) {
        matches.add(option);
      }
    }
    return matches;
  }
}
