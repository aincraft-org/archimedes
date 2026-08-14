package dev.jlo.ships.command;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/** Tab completion for {@code /ship}: offers the three known subcommands. */
public final class ShipTabCompleter implements org.bukkit.command.TabCompleter {
  /** Known ship subcommands. */
  private static final List<String> SUBCOMMANDS = List.of("assemble", "inspect", "disassemble");

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
    return List.of();
  }
}
