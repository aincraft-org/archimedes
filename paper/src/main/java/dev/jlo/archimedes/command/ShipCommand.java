package dev.jlo.archimedes.command;

import dev.jlo.archimedes.config.ShipConfig;
import dev.jlo.archimedes.model.Ship;
import dev.jlo.archimedes.ship.ShipService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Executor for ship management commands. */
public final class ShipCommand implements org.bukkit.command.CommandExecutor {
  /** Ship service. */
  private final ShipService service;

  /** Ship configuration. */
  private final ShipConfig config;

  /** Target resolver. */
  private final TargetResolver targetResolver;

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
    if (args.length == 0) {
      player.sendMessage(ChatColor.RED + "Usage: /ship assemble|inspect|disassemble|buoyancy|sink");
      return true;
    }
    switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
      case "assemble":
        return permitted(player, "archimedes.assemble") && assemble(player);
      case "inspect":
        return permitted(player, "archimedes.inspect") && inspect(player);
      case "disassemble":
        return permitted(player, "archimedes.disassemble") && disassemble(player);
      case "buoyancy":
        return permitted(player, "archimedes.buoyancy") && buoyancy(player);
      case "sink":
        return permitted(player, "archimedes.sink") && sink(player, args);
      default:
        player.sendMessage(ChatColor.RED + "Unknown subcommand: " + args[0]);
        return true;
    }
  }

  private boolean permitted(Player player, String permission) {
    if (player.hasPermission(permission)) {
      return true;
    }
    player.sendMessage(ChatColor.RED + "You lack permission: " + permission);
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

  private boolean buoyancy(Player player) {
    if (service.toggleBuoyancy(player.getUniqueId(), player.getWorld().getUID())) {
      player.sendMessage(ChatColor.GREEN + "Buoyancy toggled.");
    } else {
      player.sendMessage(ChatColor.RED + "Cannot toggle buoyancy: " + service.lastError());
    }
    return true;
  }

  private boolean sink(Player player, String[] args) {
    if (args.length < 2) {
      player.sendMessage(ChatColor.RED + "Usage: /ship sink <blocks>");
      return true;
    }
    int blocks;
    try {
      blocks = Integer.parseInt(args[1]);
    } catch (NumberFormatException exception) {
      player.sendMessage(ChatColor.RED + "Invalid block count: " + args[1]);
      return true;
    }
    if (blocks < 1) {
      player.sendMessage(ChatColor.RED + "Block count must be positive.");
      return true;
    }
    if (service.sink(player.getUniqueId(), player.getWorld().getUID(), blocks)) {
      player.sendMessage(ChatColor.GREEN + "Ship lowered by " + blocks + " blocks.");
    } else {
      player.sendMessage(ChatColor.RED + "Cannot lower ship: " + service.lastError());
    }
    return true;
  }
}
