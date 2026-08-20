package dev.mintychochip.archimedes.command;

import dev.mintychochip.archimedes.collision.CollisionMode;
import dev.mintychochip.archimedes.collision.CollisionSnapshot;
import dev.mintychochip.archimedes.collision.CollisionVolumeManager;
import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.phys.ShipInspection;
import dev.mintychochip.archimedes.phys.ShipInspectionLines;
import dev.mintychochip.archimedes.phys.ShipPhysics;
import dev.mintychochip.archimedes.sail.SailShipTemplate;
import dev.mintychochip.archimedes.ship.ShipService;
import dev.mintychochip.archimedes.ship.ShipTargeting;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Executor for ship management commands. */
public final class ShipCommand implements org.bukkit.command.CommandExecutor {
  /** Vehicle service. */
  private final ShipService service;

  /** Vehicle configuration. */
  private final ShipConfig config;

  /** Target resolver. */
  private final TargetResolver targetResolver;

  /** Physics facade used by inspect. */
  private final ShipPhysics physics;

  /** Collision manager used by inspect and the A/B switch. */
  private final CollisionVolumeManager collisions;

  /**
   * Creates a command executor backed by the ship service and target resolver.
   *
   * @param service service performing ship operations
   * @param config command configuration
   * @param targetResolver resolves the block targeted by the player
   * @param physics physics facade for inspect snapshots
   */
  public ShipCommand(
      ShipService service, ShipConfig config, TargetResolver targetResolver, ShipPhysics physics) {
    this(service, config, targetResolver, physics, null);
  }

  /**
   * Creates a command executor with collision inspect and mode switching.
   *
   * @param service service performing ship operations
   * @param config command configuration
   * @param targetResolver resolves the block targeted by the player
   * @param physics physics facade for inspect snapshots
   * @param collisions collision manager, or {@code null} to omit collision commands
   */
  public ShipCommand(
      ShipService service,
      ShipConfig config,
      TargetResolver targetResolver,
      ShipPhysics physics,
      CollisionVolumeManager collisions) {
    this.service = service;
    this.config = config;
    this.targetResolver = targetResolver;
    this.physics = physics;
    this.collisions = collisions;
  }

  /**
   * Dispatches a {@code /arch} subcommand.
   *
   * <p>Only players may execute ship operations; recognized subcommands are delegated to the
   * corresponding service operation and unknown or malformed input receives a usage/error message.
   *
   * @param sender command sender
   * @param command invoked command
   * @param commandLabel label used to invoke the command
   * @param args subcommand and its arguments
   * @return always {@code true}, indicating that the command handled its input
   */
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
      player.sendMessage(
          ChatColor.RED
              + "Usage: /"
              + commandLabel
              + " assemble|inspect|disassemble|kill|buoyancy|sink|sail|turn|collision");
      return true;
    }
    switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
      case "assemble":
        return permitted(player, "archimedes.assemble") && assemble(player);
      case "inspect":
        return permitted(player, "archimedes.inspect") && inspect(player);
      case "disassemble":
        return permitted(player, "archimedes.disassemble") && disassemble(player);
      case "kill":
        return permitted(player, "archimedes.kill") && kill(player, args);
      case "buoyancy":
        return permitted(player, "archimedes.buoyancy") && buoyancy(player);
      case "sink":
        return permitted(player, "archimedes.sink") && sink(player, args);
      case "sail":
        return permitted(player, "archimedes.sail") && sail(player, args);
      case "turn":
        return permitted(player, "archimedes.sail") && turn(player, args);
      case "collision":
        return collision(player, args);
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
    Vehicle ship =
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
    Vehicle ship = nearby(player);
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "No ship nearby.");
      return true;
    }
    ShipInspection report = physics.inspect(ship);
    for (String line : ShipInspectionLines.lines(report)) {
      player.sendMessage(line);
    }
    if (collisions != null) {
      CollisionSnapshot snapshot = collisions.snapshot(ship.id(), player.getUniqueId());
      String mode = snapshot.mode() == CollisionMode.FULL ? "A" : "B";
      player.sendMessage(
          "collision="
              + mode
              + " live="
              + snapshot.live()
              + " exposed="
              + snapshot.exposed()
              + " visibleToYou="
              + snapshot.visibleToPlayer());
    }
    return true;
  }

  /**
   * Switches the nearby hull between full spawn (A) and streamed spawn (B).
   *
   * @param player operator requesting the switch
   * @param args subcommand arguments
   * @return {@code true}
   */
  private boolean collision(Player player, String[] args) {
    if (!player.isOp()) {
      player.sendMessage(ChatColor.RED + "Only operators can switch collision mode.");
      return true;
    }
    if (collisions == null) {
      player.sendMessage(ChatColor.RED + "Collision is unavailable.");
      return true;
    }
    if (args.length < 2) {
      player.sendMessage(ChatColor.RED + "Usage: /arch collision a|b");
      return true;
    }
    CollisionMode mode = parseCollisionMode(args[1]);
    if (mode == null) {
      player.sendMessage(ChatColor.RED + "Usage: /arch collision a|b");
      return true;
    }
    Vehicle ship = nearby(player);
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "No ship nearby.");
      return true;
    }
    collisions.setMode(ship, mode);
    String label = mode == CollisionMode.FULL ? "A (full)" : "B (streamed)";
    player.sendMessage(ChatColor.GREEN + "Collision mode " + label + ".");
    return true;
  }

  private static CollisionMode parseCollisionMode(String token) {
    String value = token.toLowerCase(java.util.Locale.ROOT);
    if ("a".equals(value) || "full".equals(value)) {
      return CollisionMode.FULL;
    }
    if ("b".equals(value) || "streamed".equals(value)) {
      return CollisionMode.STREAMED;
    }
    return null;
  }

  private boolean disassemble(Player player) {
    Vehicle ship = nearby(player);
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "No ship nearby.");
      return true;
    }
    if (!service.disassemble(ship.id(), player.getUniqueId(), player.isOp())) {
      player.sendMessage(ChatColor.RED + "Cannot disassemble: " + service.lastError());
      return true;
    }
    player.sendMessage(ChatColor.GREEN + "Disassembled ship.");
    return true;
  }

  private boolean kill(Player player, String[] args) {
    if (args.length >= 2 && "all".equalsIgnoreCase(args[1])) {
      return killAll(player);
    }
    Vehicle ship = nearby(player);
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "No ship nearby.");
      return true;
    }
    if (!service.kill(ship.id(), player.getUniqueId(), player.isOp())) {
      player.sendMessage(ChatColor.RED + "Cannot kill: " + service.lastError());
      return true;
    }
    player.sendMessage(ChatColor.GREEN + "Killed ship.");
    return true;
  }

  private boolean killAll(Player player) {
    if (!player.isOp()) {
      player.sendMessage(ChatColor.RED + "Only operators can kill all ships.");
      return true;
    }
    int killed = service.killAll();
    player.sendMessage(ChatColor.GREEN + "Killed " + killed + " ships.");
    return true;
  }

  private Vehicle nearby(Player player) {
    org.bukkit.Location location = player.getLocation();
    return ShipTargeting.nearest(
        service.all(),
        player.getWorld().getUID(),
        location.getX(),
        location.getY(),
        location.getZ(),
        config.targetDistance());
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
      player.sendMessage(ChatColor.RED + "Usage: /arch sink <blocks>");
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

  private boolean sail(Player player, String[] args) {
    SailShipTemplate.Spec spec = parseSailSpec(args, player);
    if (spec == null) {
      player.sendMessage(
          ChatColor.RED + "Usage: /arch sail [small|medium|large] [mesh] [north|south|east|west]");
      return true;
    }
    org.bukkit.block.BlockFace look = player.getFacing();
    int x = player.getLocation().getBlockX() + look.getModX() * 3;
    int y = player.getLocation().getBlockY();
    int z = player.getLocation().getBlockZ() + look.getModZ() * 3;
    Vehicle ship =
        service.spawnSail(player.getUniqueId(), player.getWorld().getUID(), x, y, z, spec.token());
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "Cannot spawn sail: " + service.lastError());
      return true;
    }
    player.sendMessage(
        ChatColor.GREEN + "Spawned sail ship " + ship.id().toString().substring(0, 8) + ".");
    return true;
  }

  private boolean turn(Player player, String[] args) {
    if (args.length < 2) {
      player.sendMessage(ChatColor.RED + "Usage: /arch turn [north|south|east|west|left|right]");
      return true;
    }
    Vehicle ship = nearby(player);
    if (ship == null) {
      player.sendMessage(ChatColor.RED + "No ship nearby.");
      return true;
    }
    if (!service.turnSail(ship.id(), player.getUniqueId(), player.isOp(), args[1])) {
      player.sendMessage(ChatColor.RED + "Cannot turn sail: " + service.lastError());
      return true;
    }
    player.sendMessage(
        ChatColor.GREEN + "Turned sail " + args[1].toLowerCase(java.util.Locale.ROOT) + ".");
    return true;
  }

  private static SailShipTemplate.Spec parseSailSpec(String[] args, Player player) {
    SailShipTemplate.Facing look = playerFacing(player);
    if (args.length <= 1) {
      return new SailShipTemplate.Spec(
          SailShipTemplate.Size.MEDIUM, SailShipTemplate.Shape.FLAT, look);
    }
    StringBuilder joined = new StringBuilder(args[1].toLowerCase(java.util.Locale.ROOT));
    boolean namedFacing = SailShipTemplate.Facing.parse(args[1]) != null;
    for (int i = 2; i < args.length; i++) {
      joined.append('-').append(args[i].toLowerCase(java.util.Locale.ROOT));
      if (SailShipTemplate.Facing.parse(args[i]) != null) {
        namedFacing = true;
      }
    }
    SailShipTemplate.Spec parsed = SailShipTemplate.Spec.parse(joined.toString());
    if (parsed == null) {
      return null;
    }
    if (namedFacing) {
      return parsed;
    }
    return new SailShipTemplate.Spec(parsed.size(), parsed.shape(), look);
  }

  private static SailShipTemplate.Facing playerFacing(Player player) {
    org.bukkit.block.BlockFace look = player.getFacing();
    SailShipTemplate.Facing facing = SailShipTemplate.Facing.parse(look.name());
    if (facing == null) {
      return SailShipTemplate.Facing.SOUTH;
    }
    return facing;
  }
}
