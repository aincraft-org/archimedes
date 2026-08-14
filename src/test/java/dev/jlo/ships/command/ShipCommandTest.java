package dev.jlo.ships.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.config.ShipConfig;
import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import dev.jlo.ships.ship.ShipService;
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
  private static final String SHIP = "ship";

  /** Assemble subcommand label. */
  private static final String SUB_ASSEMBLE = "assemble";

  /** Inspect subcommand label. */
  private static final String SUB_INSPECT = "inspect";

  /** Disassemble subcommand label. */
  private static final String SUB_DISASSEMBLE = "disassemble";

  /** Common world identifier. */
  private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** Records service calls and messages for assertions. */
  private static final class RecordingService implements ShipService {
    final List<String> calls = new ArrayList<>();
    final List<String> messages = new ArrayList<>();
    Ship assembled;
    Ship owned;
    String error = "boom";
    UUID owner = UUID.randomUUID();
    boolean opUser;
    int targetX;
    int targetY;
    int targetZ;
    UUID lastRequester;
    boolean lastOperator;
    boolean disassembleFails;

    @Override
    public Ship assembleAt(UUID playerId, int x, int y, int z, UUID worldId) {
      calls.add(SUB_ASSEMBLE);
      targetX = x;
      targetY = y;
      targetZ = z;
      return assembled;
    }

    @Override
    public Ship findOwnedInWorld(UUID playerId, UUID worldId) {
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
    public String lastError() {
      return error;
    }

    @Override
    public Map<UUID, Ship> loadAll() {
      return Map.of();
    }

    @Override
    public void saveAll() {}

    @Override
    public void removeAllRuntime() {}

    @Override
    public Collection<Ship> all() {
      return List.of();
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
      calls.add("sink");
      lastSinkBlocks = blocks;
      return sinkResult;
    }
  }

  /** Builds a ship with one block for service returns. */
  private static Ship ship() {
    ShipOrigin origin = new ShipOrigin(WORLD_ID, 1, 2, 3);
    return new Ship(
        UUID.randomUUID(),
        UUID.randomUUID(),
        origin,
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
  }

  /** Player proxy carrying permissions, world, and target block. */
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
      new org.bukkit.command.Command("ship") {
        @Override
        public boolean execute(
            org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
          return true;
        }
      };

  private static ShipCommand command(RecordingService service, TargetResolver resolver) {
    return new ShipCommand(service, config(), resolver);
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
    commandNoTarget(service).onCommand(player, CMD, "ship", new String[0]);
    assertTrue(service.messages.get(0).contains("Usage"));
    assertTrue(service.calls.isEmpty());
  }

  @Test
  void rejectsUnknownSubcommand() {
    RecordingService service = new RecordingService();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {"sail"});
    assertTrue(service.messages.get(0).contains("Unknown subcommand"));
    assertTrue(service.calls.isEmpty());
  }

  @Test
  void rejectsAssembleWithoutPermission() {
    RecordingService service = new RecordingService();
    Player player = player(service, false);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_ASSEMBLE});
    assertTrue(service.messages.get(0).contains("lack permission"));
    assertTrue(service.calls.isEmpty());
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
  void inspectRequiresOwnedShip() {
    RecordingService service = new RecordingService();
    service.owned = null;
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_INSPECT});
    assertTrue(service.messages.get(0).contains("No ship in this world"));
    assertEquals(List.of("find"), service.calls);
  }

  @Test
  void inspectReportsShipSummary() {
    RecordingService service = new RecordingService();
    service.owned = ship();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_INSPECT});
    assertTrue(service.messages.get(0).contains("Ship "));
    assertTrue(service.messages.get(0).contains("blocks=1"));
  }

  @Test
  void disassembleDelegatesWithOwnership() {
    RecordingService service = new RecordingService();
    service.owned = ship();
    service.opUser = false;
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {SUB_DISASSEMBLE});
    assertEquals(List.of("find", SUB_DISASSEMBLE), service.calls);
    assertEquals(service.owner, service.lastRequester);
    assertFalse(service.lastOperator);
    assertTrue(service.messages.get(0).contains("Disassembled ship"));
  }

  @Test
  void disassembleReportsServiceFailure() {
    RecordingService service = new RecordingService();
    service.error = "You do not own this ship";
    service.owned = ship();
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
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {"sink", "3"});
    assertTrue(service.calls.contains("sink"));
    assertEquals(3, service.lastSinkBlocks);
  }

  @Test
  void sinkRejectsNonNumericBlocks() {
    RecordingService service = new RecordingService();
    service.owned = ship();
    Player player = player(service, true);
    commandNoTarget(service).onCommand(player, CMD, SHIP, new String[] {"sink", "abc"});
    assertTrue(service.messages.get(0).contains("Invalid block count"));
    assertTrue(service.calls.isEmpty());
  }
}
