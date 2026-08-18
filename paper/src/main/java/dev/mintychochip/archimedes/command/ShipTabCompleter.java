package dev.mintychochip.archimedes.command;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Tab completion for {@code /arch}. */
public final class ShipTabCompleter implements org.bukkit.command.TabCompleter {
  /** Known ship subcommands. */
  private static final List<String> SUBCOMMANDS =
      List.of("assemble", "inspect", "disassemble", "buoyancy", "sink", "sail");

  /** Sail size names. */
  private static final List<String> SAIL_SIZES = List.of("small", "medium", "large");

  /**
   * Completes the first {@code /arch} argument from the known subcommands.
   *
   * <p>Only the first argument is completed; later arguments return an empty list.
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
      return SAIL_SIZES.stream().filter(size -> size.startsWith(prefix)).toList();
    }
    return List.of();
  }
}
