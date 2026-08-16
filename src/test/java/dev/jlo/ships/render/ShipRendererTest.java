package dev.jlo.ships.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import dev.jlo.ships.model.ShipPose;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.Test;

/** Behavior tests for the block-display ship renderer. */
class ShipRendererTest {
  /** Common capturable material. */
  private static final String STONE = "minecraft:stone";

  /** Common world identifier. */
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** An in-memory fake implementing the display contract via proxy. */
  private static final class FakeDisplay {
    BlockData block;
    boolean persistent = true;
    Location location;
    final Map<NamespacedKey, String> tags = new HashMap<>();

    BlockDisplay proxy() {
      return (BlockDisplay)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {BlockDisplay.class},
              (target, method, args) -> {
                switch (method.getName()) {
                  case "getBlock":
                    return block;
                  case "setBlock":
                    block = (BlockData) args[0];
                    return null;
                  case "getLocation":
                    return location;
                  case "setLocation":
                    location = (Location) args[0];
                    return null;
                  case "setPersistent":
                    persistent = (Boolean) args[0];
                    return null;
                  case "isPersistent":
                    return persistent;
                  case "teleport":
                    location = (Location) args[0];
                    return true;
                  case "getPersistentDataContainer":
                    return Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                        (container, containerMethod, containerArgs) -> {
                          if ("set".equals(containerMethod.getName())) {
                            tags.put((NamespacedKey) containerArgs[0], (String) containerArgs[2]);
                            return null;
                          }
                          if ("get".equals(containerMethod.getName())) {
                            return tags.get(containerArgs[0]);
                          }
                          return defaultFor(containerMethod.getReturnType());
                        });
                  default:
                    return defaultFor(method.getReturnType());
                }
              });
    }
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
    return null;
  }

  private static final class SpySurface implements RenderSurface {
    final List<BlockDisplay> spawned = new ArrayList<>();

    final List<Location> teleports = new ArrayList<>();
    final Map<String, BlockData> dataById = new HashMap<>();
    final List<UUID> renderedShips = new ArrayList<>();
    RuntimeException renderFailure;

    @Override
    public BlockDisplay spawnBlockDisplay(
        Location location, java.util.function.Consumer<BlockDisplay> config) {
      if (renderFailure != null) {
        throw renderFailure;
      }
      FakeDisplay fake = new FakeDisplay();
      fake.location = location;
      BlockDisplay proxy = fake.proxy();
      config.accept(proxy);
      spawned.add(proxy);
      teleports.add(location);
      return proxy;
    }

    @Override
    public BlockData blockData(String serialized) {
      return dataById.get(serialized);
    }

    @Override
    public Collection<BlockDisplay> tagged(NamespacedKey key, String shipId) {
      List<BlockDisplay> reversed = new ArrayList<>(spawned);
      java.util.Collections.reverse(reversed);
      return reversed;
    }

    @Override
    public void teleport(org.bukkit.entity.Entity entity, Location location) {
      teleports.add(location);
    }

    @Override
    public UUID worldUuid() {
      return WORLD;
    }

    @Override
    public Location location(ShipOrigin origin, double dx, double dy, double dz) {
      return new Location(null, origin.x() + dx, origin.y() + dy, origin.z() + dz);
    }

    @Override
    public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {
      renderedShips.add(shipId);
    }

    @Override
    public void removeTagged(NamespacedKey key, String shipId) {
      // Test surface: no live entities to remove.
    }
  }

  @Test
  void renderNormalizesRuntimeFailureWithShipIdAndCause() {
    Ship ship = shipWithBlock(0, 0, 0, STONE);
    RuntimeException cause = new IllegalStateException("render boom");
    SpySurface surface = new SpySurface();
    surface.renderFailure = cause;
    dev.jlo.ships.ship.ShipRuntimeException failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            dev.jlo.ships.ship.ShipRuntimeException.class,
            () ->
                new dev.jlo.ships.bukkit.BukkitShipRenderer(
                        surface, new NamespacedKey("ships", "test"))
                    .render(ship, ignored -> {}));
    assertEquals("Bukkit render failed for ship " + ship.id(), failure.getMessage());
    org.junit.jupiter.api.Assertions.assertSame(cause, failure.getCause());
  }

  @Test
  void rendersSpawnedDisplayAtIntegerAlignedCorner() {
    SpySurface surface = new SpySurface();
    Ship ship = shipWithBlock(10, 20, 30, STONE);
    new ShipRenderer().render(ship, surface);
    BlockDisplay display = surface.spawned.get(0);
    assertEquals(110.0, display.getLocation().getX(), 0.001);
    assertEquals(220.0, display.getLocation().getY(), 0.001);
    assertEquals(330.0, display.getLocation().getZ(), 0.001);
  }

  @Test
  void appliesBlockDataToDisplay() {
    SpySurface surface = new SpySurface();
    Ship ship = shipWithBlock(0, 0, 0, STONE);
    new ShipRenderer().render(ship, surface);
    assertEquals(surface.dataById.get(STONE), surface.spawned.get(0).getBlock());
  }

  @Test
  void reportsRenderedShipToSurface() {
    SpySurface surface = new SpySurface();
    Ship ship = shipWithBlock(0, 0, 0, STONE);
    new ShipRenderer().render(ship, surface);
    assertEquals(1, surface.renderedShips.size());
    assertEquals(ship.id(), surface.renderedShips.get(0));
  }

  @Test
  void rendersDisplayAtPose() {
    SpySurface surface = new SpySurface();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)),
            new ShipPose(2.5),
            true);
    new ShipRenderer().render(ship, surface);
    BlockDisplay display = surface.spawned.get(0);
    assertEquals(202.5, display.getLocation().getY(), 0.001);
  }

  @Test
  void repeatedRepositionUsesModelCoordinatesWithoutDrift() {
    SpySurface surface = new SpySurface();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(2, -1, 3), STONE)),
            new ShipPose(2.5),
            true);
    new ShipRenderer().render(ship, surface);
    dev.jlo.ships.bukkit.BukkitShipRenderer renderer =
        new dev.jlo.ships.bukkit.BukkitShipRenderer(surface, new NamespacedKey("ships", "test"));
    ship.setPose(new ShipPose(4.0));
    renderer.reposition(ship, 2.5, 4.0);
    ship.setPose(new ShipPose(1.25));
    renderer.reposition(ship, 4.0, 1.25);
    Location location = surface.teleports.get(surface.teleports.size() - 1);
    assertEquals(102.0, location.getX(), 0.001);
    assertEquals(201.5, location.getY(), 0.001);
    assertEquals(303.0, location.getZ(), 0.001);
  }

  @Test
  void reversedTaggedIterationPreservesBlockIdentity() {
    SpySurface surface = new SpySurface();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(
                new ShipBlock(new BlockPos(1, 0, 0), STONE),
                new ShipBlock(new BlockPos(9, 0, 0), STONE)),
            new ShipPose(0.0),
            true);
    dev.jlo.ships.bukkit.BukkitShipRenderer renderer =
        new dev.jlo.ships.bukkit.BukkitShipRenderer(surface, new NamespacedKey("ships", "test"));
    renderer.render(ship, ignored -> {});
    ship.setPose(new ShipPose(2.0));
    renderer.reposition(ship, 0.0, 2.0);
    java.util.Set<Double> repositionedX =
        java.util.Set.of(surface.teleports.get(2).getX(), surface.teleports.get(3).getX());
    assertEquals(java.util.Set.of(101.0, 109.0), repositionedX);
  }

  private static Ship shipWithBlock(int dx, int dy, int dz, String data) {
    ShipOrigin origin = new ShipOrigin(WORLD, 100, 200, 300);
    return new Ship(
        UUID.randomUUID(),
        UUID.randomUUID(),
        origin,
        List.of(new ShipBlock(new BlockPos(dx, dy, dz), data)));
  }
}
