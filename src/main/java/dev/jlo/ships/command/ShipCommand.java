package dev.jlo.ships.command;

import dev.jlo.ships.config.ShipConfig;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.ship.ShipService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Executor for {@code /ship assemble|inspect|disassemble}. Targets the block the player is looking
 * at and delegates state transitions to the service.
 */
public final class ShipCommand implements org.bukkit.command.CommandExecutor {
  /** The ship service. */
  private final ShipService service;

  /** The ship configuration. */
  private final ShipConfig config;

  /** The block targeting resolver. */
  private final TargetResolver targetResolver;

  /**
   * Creates the ship command bound to a service, configuration, and target resolver.
   *
   * @param service the ship service
   * @param config the ship configuration
   * @param targetResolver the target resolver
   */
  public ShipCommand(ShipService service, ShipConfig config, TargetResolver targetResolver) {
    this.service = service;
    this.config = config;
    this.targetResolver = targetResolver;
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String commandLabel,
      @NotNull String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(ChatColor.RED + "Only players can build ships.");
      return true;
    }
    if (args.length < 1) {
      player.sendMessage(ChatColor.RED + "Usage: /ship assemble|inspect|disassemble");
      return true;
    }
    String sub = args[0].toLowerCase(java.util.Locale.ROOT);
    switch (sub) {
      case "assemble":
        if (!requirePermission(player, "ships.assemble")) {
          return true;
        }
        return assemble(player);
      case "inspect":
        if (!requirePermission(player, "ships.inspect")) {
          return true;
        }
        return inspect(player);
      case "disassemble":
        if (!requirePermission(player, "ships.disassemble")) {
          return true;
        }
        return disassemble(player);
      default:
        player.sendMessage(ChatColor.RED + "Unknown subcommand: " + sub);
        return true;
    }
  }

  private boolean requirePermission(Player player, String node) {
    if (player.hasPermission(node)) {
      return true;
    }
    player.sendMessage(ChatColor.RED + "You lack permission: " + node);
    return false;
  }

  private boolean assemble(Player player) {
    TargetResolver.Target target = targetResolver.resolve(player);
    if (target == null) {
      player.sendMessage(
          ChatColor.RED + "No target block within " + config.targetDistance() + " blocks.");
      return true;
    }
    Ship ship =
        service.assembleAt(
            player.getUniqueId(), target.x(), target.y(), target.z(), target.worldId());
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "Cannot assemble: " + service.lastError());
      return true;
    }
    player.sendMessage(
        ChatColor.GREEN
            + "Assembled ship "
            + ship.id().toString().substring(0, 8)
            + " with "
            + ship.blockCount()
            + " blocks.");
    return true;
  }

  private boolean inspect(Player player) {
    Ship ship = service.findOwnedInWorld(player.getUniqueId(), player.getWorld().getUID());
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "No ship in this world.");
      return true;
    }
    player.sendMessage(
        ChatColor.GOLD
            + "Ship "
            + ship.id().toString().substring(0, 8)
            + " | blocks="
            + ship.blockCount());
    return true;
  }

  private boolean disassemble(Player player) {
    Ship ship = service.findOwnedInWorld(player.getUniqueId(), player.getWorld().getUID());
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "No ship in this world.");
      return true;
    }
    if (!service.disassemble(ship.id(), player.getUniqueId(), player.isOp())) {
      player.sendMessage(ChatColor.RED + "Cannot disassemble: " + service.lastError());
      return true;
    }
    player.sendMessage(ChatColor.GREEN + "Disassembled ship.");
    return true;
  }
}
