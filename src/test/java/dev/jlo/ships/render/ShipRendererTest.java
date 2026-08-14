package dev.jlo.ships.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.Test;

/** Behavior tests for the block-display ship renderer. */
class ShipRendererTest {
  /** An in-memory fake implementing the display contract via proxy. */
  private static final class FakeDisplay {
    BlockData block;
    boolean persistent = true;
    Location location;

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
                  default:
                    return defaultFor(method.getReturnType());
                }
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
      return null;
    }
  }

  private static final class SpySurface implements RenderSurface {
    final List<BlockDisplay> spawned = new ArrayList<>();
    final List<Location> teleports = new ArrayList<>();
    final Map<String, BlockData> dataById = new HashMap<>();
    final List<UUID> renderedShips = new ArrayList<>();

    @Override
    public BlockDisplay spawnBlockDisplay(Location location, java.util.function.Consumer<BlockDisplay> config) {
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
    public void teleport(org.bukkit.entity.Entity entity, Location location) {
      teleports.add(location);
    }

    @Override
    public UUID worldUuid() {
      return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    @Override
    public Location location(ShipOrigin origin, int dx, int dy, int dz) {
      return new Location(null, origin.x() + dx + 0.5, origin.y() + dy + 0.5, origin.z() + dz + 0.5);
    }

    @Override
    public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {
      renderedShips.add(shipId);
    }
  }

  @Test
  void rendersSpawnedDisplayAtAdjustedLocation() {
    SpySurface surface = new SpySurface();
    Ship ship = shipWithBlock(10, 20, 30, "minecraft:stone");
    new ShipRenderer().render(ship, surface);
    BlockDisplay display = surface.spawned.get(0);
    assertFalse(display.isPersistent());
    assertEquals(100 + 10 + 0.5, display.getLocation().getX(), 0.001);
    assertEquals(200 + 20 + 0.5, display.getLocation().getY(), 0.001);
    assertEquals(300 + 30 + 0.5, display.getLocation().getZ(), 0.001);
  }

  @Test
  void appliesBlockDataToDisplay() {
    SpySurface surface = new SpySurface();
    Ship ship = shipWithBlock(0, 0, 0, "minecraft:stone");
    new ShipRenderer().render(ship, surface);
    assertEquals(surface.dataById.get("minecraft:stone"), surface.spawned.get(0).getBlock());
  }

  @Test
  void reportsRenderedShipToSurface() {
    SpySurface surface = new SpySurface();
    Ship ship = shipWithBlock(0, 0, 0, "minecraft:stone");
    new ShipRenderer().render(ship, surface);
    assertEquals(1, surface.renderedShips.size());
    assertEquals(ship.id(), surface.renderedShips.get(0));
  }

  private static Ship shipWithBlock(int dx, int dy, int dz, String data) {
    ShipOrigin origin = new ShipOrigin(UUID.fromString("00000000-0000-0000-0000-000000000001"), 100, 200, 300);
    return new Ship(
        UUID.randomUUID(),
        UUID.randomUUID(),
        origin,
        List.of(new ShipBlock(new BlockPos(dx, dy, dz), data)));
  }
}