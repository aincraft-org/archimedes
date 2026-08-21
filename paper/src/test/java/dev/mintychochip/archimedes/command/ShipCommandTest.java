package dev.mintychochip.archimedes.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.collision.CollisionMode;
import dev.mintychochip.archimedes.collision.CollisionSnapshot;
import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.phys.ShipInspection;
import dev.mintychochip.archimedes.phys.ShipPhysics;
import dev.mintychochip.archimedes.ship.ShipService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Behavior tests for the {@code /ship} command executor. */
class ShipCommandTest {
  /** Common subcommand label. */
  private static final String SHIP = "arch";

  /** Assemble subcommand label. */
  private static final String SUB_ASSEMBLE = "assemble";

  /** Inspect subcommand label. */
  private static final String SUB_INSPECT = "inspect";

  /** Disassemble subcommand label. */
  private static final String SUB_DISASSEMBLE = "disassemble";

  /** Sink subcommand label. */
  private static final String SUB_SINK = "sink";

  /** Buoyancy subcommand label. */
  private static final String SUB_BUOYANCY = "buoyancy";

  /** Sail spawn subcommand label. */
  private static final String SUB_SAIL = "sail";

  /** Destroy subcommand label. */
  private static final String SUB_KILL = "kill";

  /** Shared list/kill-all token recorded by the fake service. */
  private static final String ALL = "all";

  /** Common world identifier. */
  private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** Records service calls and messages for assertions. */
  private static final class RecordingService implements ShipService {
    final List<String> calls = new ArrayList<>();
    final List<String> messages = new ArrayList<>();
    Vehicle assembled;
    Vehicle owned;
    String error = "boom";
    UUID owner = UUID.randomUUID();
    boolean opUser;
    int targetX;
    int targetY;
    int targetZ;
    UUID lastWorld;
    String lastSailSize;
    UUID lastRequester;
    boolean lastOperator;
    boolean disassembleFails;
    boolean killFails;
    final List<Vehicle> registered = new ArrayList<>();
    UUID lastKilled;
    int killAllCount;

    @Override
    public Vehicle assembleAt(UUID playerId, int x, int y, int z, UUID worldId) {
      calls.add(SUB_ASSEMBLE);
      targetX = x;
      targetY = y;
      targetZ = z;
      return assembled;
    }

    @Override
    public Vehicle spawnSail(UUID playerId, UUID worldId, int x, int y, int z, String size) {
      calls.add(SUB_SAIL);
      lastRequester = playerId;
      lastWorld = worldId;
      lastSailSize = size;
      targetX = x;
      targetY = y;
      targetZ = z;
      return assembled;
    }

    @Override
    public Vehicle findOwnedInWorld(UUID playerId, UUID worldId) {
      calls.add("find");
      return owned;
    }

    @Override
    public boolean disassemble(UUID shipId, UUID requesterId, boolean operator) {
      calls.add(SUB_DISASSEMBLE);
      lastRequester = requesterId;
      lastOperator = operator;
      return !disassembleFails;
    }

    @Override
    public boolean kill(UUID shipId, UUID requesterId, boolean operator) {
      calls.add(SUB_KILL);
      lastKilled = shipId;
      lastRequester = requesterId;
      lastOperator = operator;
      return !killFails;
    }

    @Override
    public int killAll() {
      calls.add("killAll");
      return killAllCount;
    }

    @Override
    public String lastError() {
      return error;
    }

    @Override
    public Map<UUID, Vehicle> loadAll() {
      return Map.of();
    }

    @Override
    public void saveAll() {}

    @Override
    public void removeAllRuntime() {}

    @Override
    public Collection<Vehicle> all() {
      calls.add(ALL);
      if (!registered.isEmpty()) {
        return List.copyOf(registered);
      }
      return owned == null ? List.of() : List.of(owned);
    }

    @Override
    public void tick() {}

    boolean toggleBuoyancyResult = true;
    boolean sinkResult = true;
    int lastSinkBlocks;

    @Override
    public boolean toggleBuoyancy(UUID requesterId, UUID worldId) {
      calls.add("toggleBuoyancy");
      return toggleBuoyancyResult;
    }

    @Override
    public boolean sink(UUID requesterId, UUID worldId, int blocks) {
      calls.add(SUB_SINK);
      lastSinkBlocks = blocks;
      return sinkResult;
    }
  }

  /** Builds a ship with one block for service returns. */
  private static Vehicle ship() {
    return shipAt(1, 2, 3);
  }

  /** Builds a one-block ship at the given origin. */
  private static Vehicle shipAt(int x, int y, int z) {
    ShipOrigin origin = new ShipOrigin(WORLD_ID, x, y, z);
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        origin,
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
  }

  /** Vehicle placed under the test player at (10, 64, 20). */
  private static Vehicle nearbyShip() {
    return shipAt(10, 64, 20);
  }

  /** World proxy returning the fixed world identifier. */
  private static final World WORLD_PROXY =
      (World)
          Proxy.newProxyInstance(
              ShipCommandTest.class.getClassLoader(),
              new Class<?>[] {World.class},
              (proxy, method, args) ->
                  method.getName().equals("getUID")
                      ? WORLD_ID
                      : defaultFor(method.getReturnType()));

  private static Player player(RecordingService service, boolean permission) {
    return player(service, permission, org.bukkit.block.BlockFace.SOUTH);
  }

  private static Player player(
      RecordingService service, boolean permission, org.bukkit.block.BlockFace facing) {
    return (Player)
        Proxy.newProxyInstance(
            ShipCommandTest.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "hasPermission":
                  return permission;
                case "getUniqueId":
                  return service.owner;
                case "isOp":
                  return service.opUser;
                case "getWorld":
                  return WORLD_PROXY;
                case "getLocation":
                  return new org.bukkit.Location(WORLD_PROXY, 10, 64, 20);
                case "getFacing":
                  return facing;
                case "sendMessage":
                  service.messages.add(String.valueOf(args[0]));
                  return null;
                default:
                  return defaultFor(method.getReturnType());
              }
            });
  }

  private static World world() {
    return (World)
        Proxy.newProxyInstance(
            ShipCommandTest.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getUID")) {
                return WORLD_ID;
              }
              return defaultFor(method.getReturnType());
            });
  }

  private static Object defaultFor(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == double.class) {
      return 0.0;
    }
    if (type == float.class) {
      return 0.0f;
    }
    if (type == UUID.class) {
      return UUID.randomUUID();
    }
    return null;
  }

  /** Concrete command instance for executor invocation. */
  private static final org.bukkit.command.Command CMD =
      new org.bukkit.command.Command("arch") {
        @Override
        public boolean execute(
            org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
          return true;
        }
      };

  private static ShipCommand command(RecordingService service, TargetResolver resolver) {
    return new ShipCommand(service, config(), resolver, unusedPhysics());
  }

  private static ShipPhysics unusedPhysics() {
    return new ShipPhysics() {
      @Override
      public boolean tick(dev.mintychochip.archimedes.model.Vehicle ship) {
        return false;
      }

      @Override
      public boolean rise(dev.mintychochip.archimedes.model.Vehicle ship) {
        return true;
      }

      @Override
      public boolean sink(dev.mintychochip.archimedes.model.Vehicle ship, int blocks) {
        return false;
      }

      @Override
      public void clear(dev.mintychochip.archimedes.model.Vehicle ship) {}

      @Override
      public ShipInspection inspect(dev.mintychochip.archimedes.model.Vehicle ship) {
        return new ShipInspection(
            ship.id(),
            ship.blockCount(),
            0,
            0,
            12.5,
            ship.buoyancyEnabled(),
            true,
            ship.pose().x(),
            ship.pose().y(),
            ship.pose().z(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1_000_000L,
            List.of(new ShipInspection.ForceLine("Gravity", 0, -10, 0, 0, 0, 0)),
            0,
            -10,
            0);
      }
    };
  }

  private static ShipCommand commandNoTarget(RecordingService service) {
    return command(service, player1 -> null);
  }

  private static TargetResolver target(int x, int y, int z) {
    return player1 -> new TargetResolver.Target(x, y, z, WORLD_ID);
  }

  private static ShipConfig config() {
    return new ShipConfig(
        64, 5, java.util.Set.of(), java.util.Set.of(), true, 1, 0.5, 16.0, 0.05, 1.0, 0.5, 0.9);
  }

  @Test
  void rejectsConsoleSender() {
    RecordingService service = new RecordingService();
    CommandSender sender =
        (CommandSender)
            Proxy.newProxyInstance(
                ShipCommandTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("sendMessage")) {
                    service.messages.add(String.valueOf(args[0]));
                    return null;
                  }
                  return defaultFor(method.getReturnType());
                });
    boolean handled =
        commandNoTarget(service).onCommand(sender, CMD, SHIP, new String[] {SUB_ASSEMBLE});
    assertTrue(handled);
    assertTrue(service.messages.get(0).contains("Only players"));
    assertTrue(service.calls.isEmpty());
  }

  @Test
  void rejectsMissingArgs() {
    RecordingService service = new RecordingService();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, "arch", new String[0]);
    assertTrue(service.messages.get(0).contains("Usage"));
    assertTrue(service.messages.get(0).contains("/arch"));
    assertTrue(service.calls.isEmpty());
  }

  @Test
  void rejectsUnknownSubcommand() {
    RecordingService service = new RecordingService();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {"nope"});
    assertTrue(service.messages.get(0).contains("Unknown subcommand"));
    assertTrue(service.calls.isEmpty());
  }

  @Test
  void rejectsInspectWithoutPermission() {
    RecordingService service = new RecordingService();
    commandNoTarget(service)
        .onCommand(player(service, false), CMD, SHIP, new String[] {SUB_INSPECT});
    assertEquals(0, service.calls.size());
  }

  @Test
  void rejectsDisassembleWithoutPermission() {
    RecordingService service = new RecordingService();
    commandNoTarget(service)
        .onCommand(player(service, false), CMD, SHIP, new String[] {SUB_DISASSEMBLE});
    assertEquals(0, service.calls.size());
  }

  @Test
  void rejectsBuoyancyWithoutPermission() {
    RecordingService service = new RecordingService();
    commandNoTarget(service)
        .onCommand(player(service, false), CMD, SHIP, new String[] {SUB_BUOYANCY});
    assertEquals(0, service.calls.size());
  }

  @Test
  void rejectsSinkWithoutPermission() {
    RecordingService service = new RecordingService();
    commandNoTarget(service)
        .onCommand(player(service, false), CMD, SHIP, new String[] {SUB_SINK, "1"});
    assertEquals(0, service.calls.size());
  }

  @Test
  void rejectsNoTargetBlock() {
    RecordingService service = new RecordingService();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_ASSEMBLE});
    assertTrue(service.messages.get(0).contains("No target block"));
    assertTrue(service.calls.isEmpty());
  }

  @Test
  void assemblesWithTargetCoordinates() {
    RecordingService service = new RecordingService();
    service.assembled = ship();
    Player player = player(service, true);
    command(service, target(10, 20, 30)).onCommand(player, CMD, SHIP, new String[] {SUB_ASSEMBLE});
    assertEquals(List.of(SUB_ASSEMBLE), service.calls);
    assertEquals(10, service.targetX);
    assertEquals(20, service.targetY);
    assertEquals(30, service.targetZ);
    assertTrue(service.messages.get(0).contains("Assembled ship"));
  }

  @Test
  void inspectRequiresNearbyShip() {
    RecordingService service = new RecordingService();
    service.owned = shipAt(0, 64, 790);
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_INSPECT});
    assertTrue(service.messages.get(0).contains("No ship nearby"));
    assertTrue(service.calls.contains(ALL));
    assertFalse(service.calls.contains("find"));
  }

  @Test
  void inspectReportsShipSummary() {
    RecordingService service = new RecordingService();
    service.owned = nearbyShip();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_INSPECT});
    String joined = String.join("\n", service.messages);
    assertTrue(joined.contains("Arch "));
    assertTrue(joined.contains("blocks=1"));
    assertTrue(joined.contains("mass="));
    assertTrue(joined.contains("Gravity"));
    assertTrue(joined.contains("sample="));
    assertTrue(joined.contains("net "));
  }

  @Test
  void inspectPicksNearbyHullOverFirstOwnedShip() {
    RecordingService service = new RecordingService();
    Vehicle distant = shipAt(0, 64, 790);
    Vehicle nearby = nearbyShip();
    service.owned = distant;
    service.registered.add(distant);
    service.registered.add(nearby);
    commandNoTarget(service)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_INSPECT});
    String joined = String.join("\n", service.messages);
    assertTrue(joined.contains(nearby.id().toString().substring(0, 8)));
    assertFalse(joined.contains(distant.id().toString().substring(0, 8)));
  }

  @Test
  void disassembleDelegatesWithOwnership() {
    RecordingService service = new RecordingService();
    service.owned = nearbyShip();
    service.opUser = false;
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_DISASSEMBLE});
    assertTrue(service.calls.contains(SUB_DISASSEMBLE));
    assertEquals(service.owner, service.lastRequester);
    assertFalse(service.lastOperator);
    assertTrue(service.messages.get(0).contains("Disassembled ship"));
  }

  @Test
  void killDestroysNearbyShip() {
    RecordingService service = new RecordingService();
    Vehicle nearby = nearbyShip();
    service.owned = nearby;
    commandNoTarget(service).onCommand(player(service, true), CMD, SHIP, new String[] {SUB_KILL});
    assertTrue(service.calls.contains(SUB_KILL));
    assertEquals(nearby.id(), service.lastKilled);
    assertEquals(service.owner, service.lastRequester);
    assertTrue(service.messages.get(0).contains("Killed ship"));
  }

  @Test
  void killReportsServiceFailure() {
    RecordingService service = new RecordingService();
    service.owned = nearbyShip();
    service.killFails = true;
    service.error = "You do not own this ship";
    commandNoTarget(service).onCommand(player(service, true), CMD, SHIP, new String[] {SUB_KILL});
    assertTrue(service.messages.get(0).contains("Cannot kill: You do not own this ship"));
  }

  @Test
  void killRequiresNearbyShip() {
    RecordingService service = new RecordingService();
    service.owned = shipAt(0, 64, 790);
    commandNoTarget(service).onCommand(player(service, true), CMD, SHIP, new String[] {SUB_KILL});
    assertTrue(service.messages.get(0).contains("No ship nearby"));
    assertFalse(service.calls.contains(SUB_KILL));
  }

  @Test
  void killRejectsWithoutPermission() {
    RecordingService service = new RecordingService();
    commandNoTarget(service).onCommand(player(service, false), CMD, SHIP, new String[] {SUB_KILL});
    assertEquals(0, service.calls.size());
  }

  @Test
  void killAllRequiresOperator() {
    RecordingService service = new RecordingService();
    service.opUser = false;
    commandNoTarget(service)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_KILL, ALL});
    assertTrue(service.messages.get(0).contains("Only operators can kill all ships"));
    assertFalse(service.calls.contains("killAll"));
  }

  @Test
  void killAllDestroysEveryShipWhenOperator() {
    RecordingService service = new RecordingService();
    service.opUser = true;
    service.killAllCount = 4;
    commandNoTarget(service)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_KILL, ALL});
    assertTrue(service.calls.contains("killAll"));
    assertTrue(service.messages.get(0).contains("Killed 4 ships"));
  }

  @Test
  void disassembleReportsServiceFailure() {
    RecordingService service = new RecordingService();
    service.error = "You do not own this ship";
    service.owned = nearbyShip();
    service.disassembleFails = true;
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_DISASSEMBLE});
    assertTrue(service.messages.get(0).contains("Cannot disassemble: You do not own this ship"));
  }

  @Test
  void buoyancyTogglesOwnedShip() {
    RecordingService service = new RecordingService();
    service.owned = ship();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {"buoyancy"});
    assertTrue(service.calls.contains("toggleBuoyancy"));
    assertTrue(service.messages.get(0).contains("Buoyancy"));
  }

  @Test
  void sinkLowersOwnedShip() {
    RecordingService service = new RecordingService();
    service.owned = ship();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_SINK, "3"});
    assertTrue(service.calls.contains(SUB_SINK));
    assertEquals(3, service.lastSinkBlocks);
    assertTrue(
        service.messages.stream()
            .anyMatch(message -> message.contains("Ship lowered by 3 blocks.")));
  }

  @Test
  void sinkRejectsZeroAndNegativeBlocks() {
    RecordingService service = new RecordingService();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_SINK, "0"});
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_SINK, "-1"});
    assertTrue(service.calls.isEmpty());
    assertTrue(service.messages.get(0).contains("Block count must be positive."));
    assertTrue(service.messages.get(1).contains("Block count must be positive."));
  }

  @Test
  void sinkIgnoresExtraArguments() {
    RecordingService service = new RecordingService();
    service.owned = ship();
    commandNoTarget(service)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_SINK, "3", "extra"});
    assertEquals(3, service.lastSinkBlocks);
  }

  @Test
  void sinkRejectsNonNumericBlocks() {
    RecordingService service = new RecordingService();
    service.owned = ship();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_SINK, "abc"});
    assertTrue(service.messages.get(0).contains("Invalid block count"));
    assertTrue(service.calls.isEmpty());
  }

  @Test
  void sinkReportsServiceFailureWithSingleOperationPrefix() {
    RecordingService service = new RecordingService();
    service.owned = ship();
    service.sinkResult = false;
    service.error = "path blocked";
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_SINK, "3"});
    assertTrue(service.messages.get(0).contains("Cannot lower ship: path blocked"));
  }

  @Test
  void sailSpawnsPredeterminedShipInFrontOfPlayer() {
    RecordingService service = new RecordingService();
    service.assembled = ship();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_SAIL});
    assertEquals(List.of(SUB_SAIL), service.calls);
    assertEquals(service.owner, service.lastRequester);
    assertEquals(WORLD_ID, service.lastWorld);
    assertEquals(10, service.targetX);
    assertEquals(64, service.targetY);
    assertEquals(23, service.targetZ);
    assertEquals("medium", service.lastSailSize);
    assertTrue(service.messages.get(0).contains("Spawned sail ship"));
  }

  @Test
  void sailAcceptsNamedSizes() {
    RecordingService service = new RecordingService();
    service.assembled = ship();
    commandNoTarget(service)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_SAIL, "large"});
    assertEquals("large", service.lastSailSize);
  }

  @Test
  void sailRejectsUnknownSize() {
    RecordingService service = new RecordingService();
    commandNoTarget(service)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_SAIL, "huge"});
    assertTrue(service.calls.isEmpty());
    assertTrue(service.messages.get(0).contains("Usage: /arch sail"));
  }

  @Test
  void sailAcceptsMeshToken() {
    RecordingService service = new RecordingService();
    service.assembled = ship();
    commandNoTarget(service)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_SAIL, "mesh"});
    assertEquals("mesh", service.lastSailSize);
  }

  @Test
  void sailAcceptsSizePlusMeshToken() {
    RecordingService service = new RecordingService();
    service.assembled = ship();
    commandNoTarget(service)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_SAIL, "large", "mesh"});
    assertEquals("large-mesh", service.lastSailSize);
  }

  @Test
  void sailRejectsWithoutPermission() {
    RecordingService service = new RecordingService();
    commandNoTarget(service).onCommand(player(service, false), CMD, SHIP, new String[] {SUB_SAIL});
    assertTrue(service.calls.isEmpty());
  }

  @Test
  void sailReportsServiceFailure() {
    RecordingService service = new RecordingService();
    service.assembled = null;
    service.error = "Ship assembly is disabled in this world";
    commandNoTarget(service).onCommand(player(service, true), CMD, SHIP, new String[] {SUB_SAIL});
    assertEquals(List.of(SUB_SAIL), service.calls);
    assertTrue(service.messages.get(0).contains("Cannot spawn sail: Ship assembly is disabled"));
  }

  @Test
  void collisionSwitchRequiresOperator() {
    RecordingService service = new RecordingService();
    service.owned = nearbyShip();
    service.opUser = false;
    RecordingCollision collisions = new RecordingCollision();
    command(service, player1 -> null, collisions)
        .onCommand(player(service, true), CMD, SHIP, new String[] {"collision", "a"});
    assertTrue(service.messages.get(0).contains("Only operators"));
    assertEquals(null, collisions.last);
  }

  @Test
  void collisionSwitchSetsStreamedMode() {
    RecordingService service = new RecordingService();
    service.owned = nearbyShip();
    service.opUser = true;
    RecordingCollision collisions = new RecordingCollision();
    command(service, player1 -> null, collisions)
        .onCommand(player(service, true), CMD, SHIP, new String[] {"collision", "b"});
    assertEquals(CollisionMode.STREAMED, collisions.mode);
    assertEquals(service.owned.id(), collisions.last.id());
    assertTrue(service.messages.get(0).contains("Collision mode B (streamed)"));
  }

  @Test
  void inspectAppendsCollisionSnapshot() {
    RecordingService service = new RecordingService();
    service.owned = nearbyShip();
    RecordingCollision collisions = new RecordingCollision();
    command(service, player1 -> null, collisions)
        .onCommand(player(service, true), CMD, SHIP, new String[] {SUB_INSPECT});
    String joined = String.join("\n", service.messages);
    assertTrue(joined.contains("collision=B live=2 exposed=10 visibleToYou=2"));
  }

  private static ShipCommand command(
      RecordingService service, TargetResolver resolver, RecordingCollision collisions) {
    return new ShipCommand(service, config(), resolver, unusedPhysics(), collisions);
  }

  private static final class RecordingCollision
      implements dev.mintychochip.archimedes.collision.CollisionVolumeManager {
    CollisionMode mode = CollisionMode.STREAMED;
    Vehicle last;

    @Override
    public void spawn(Vehicle ship) {}

    @Override
    public void move(Vehicle ship) {}

    @Override
    public void rollback(Vehicle ship, double oldY) {}

    @Override
    public void remove(UUID shipId) {}

    @Override
    public void removeAll() {}

    @Override
    public CollisionMode mode(UUID shipId) {
      return mode;
    }

    @Override
    public void setMode(Vehicle ship, CollisionMode nextMode) {
      this.mode = nextMode;
      this.last = ship;
    }

    @Override
    public CollisionSnapshot snapshot(UUID shipId, UUID playerId) {
      return new CollisionSnapshot(mode, 2, 10, 2);
    }
  }
}
