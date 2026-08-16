package dev.jlo.ships.bukkit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import dev.jlo.ships.ship.ShipRuntimeException;
import dev.jlo.ships.render.RenderSurface;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.junit.jupiter.api.Test;

class BukkitShipRendererTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void cleanupPreservesPrimaryAndSuppressesRuntimeFailure() {
    ShipRuntimeException primary = new ShipRuntimeException(new IllegalStateException("primary"));
    RuntimeException cleanup = new IllegalArgumentException("cleanup");
    RenderSurface surface = surfaceThatFails(primary, cleanup);

    ShipRuntimeException thrown =
        assertThrows(
            ShipRuntimeException.class,
            () -> {
              Ship ship = ship();
              new BukkitShipRenderer(surface, new NamespacedKey("ships", "test")).render(ship, ignored -> {
                throw primary;
              });
            });

    assertSame(primary, thrown);
    assertTrue(List.of(thrown.getSuppressed()).contains(cleanup));
  }

  @Test
  void repositionWrapsRuntimeTeleportFailureWithShipContext() {
    RuntimeException teleport = new IllegalStateException("teleport");
    RenderSurface surface = surfaceThatFails(new RuntimeException("unused"), teleport);
    BukkitShipRenderer renderer = new BukkitShipRenderer(surface, new NamespacedKey("ships", "test"));
    Ship ship = ship();
    renderer.render(ship, ignored -> {});

    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> renderer.reposition(ship, 0, 1));
    assertSame(teleport, thrown.getCause());
    assertTrue(thrown.getMessage().contains(ship.id().toString()));
  }

  @Test
  void repositionNormalizesPairingRuntimeFailureWithShipContext() {
    RuntimeException pairing = new IllegalStateException("pairing");
    RenderSurface surface = new PairingFailureSurface(pairing);
    BukkitShipRenderer renderer = new BukkitShipRenderer(surface, new NamespacedKey("ships", "test"));

    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> renderer.reposition(ship(), 0, 1));

    assertSame(pairing, thrown.getCause());
    assertTrue(thrown.getMessage().contains("Renderer reposition failed"));
  }

  private static RenderSurface surfaceThatFails(RuntimeException... failures) {
    return new RenderSurface() {
      private boolean first = true;
      private final BlockDisplay display = (BlockDisplay) Proxy.newProxyInstance(
          BlockDisplay.class.getClassLoader(), new Class<?>[] {BlockDisplay.class},
          (proxy, method, args) -> {
            if (method.getName().equals("getPersistentDataContainer")) {
              return Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                  (container, containerMethod, containerArgs) -> {
                    if ("set".equals(containerMethod.getName())) return null;
                    if ("get".equals(containerMethod.getName())) return "0,0,0";
                    return defaultValue(containerMethod.getReturnType());
                  });
            }
            if (method.getName().equals("getLocation")) return new Location(null, 0, 0, 0);
            return defaultValue(method.getReturnType());
          });

      @Override public BlockDisplay spawnBlockDisplay(Location location, java.util.function.Consumer<BlockDisplay> config) {
        config.accept(display);
        return display;
      }
      @Override public BlockData blockData(String serialized) { return null; }
      @Override public void teleport(org.bukkit.entity.Entity entity, Location location) {
        if (failures.length > 1) {
          throw failures[1];
        }
      }
      @Override public UUID worldUuid() { return WORLD; }
      @Override public Location location(ShipOrigin origin, double dx, double dy, double dz) { return new Location(null, dx, dy, dz); }
      @Override public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {}
      @Override public void removeTagged(NamespacedKey key, String shipId) {
        if (failures.length > 1) throw failures[1];
      }
      @Override public Collection<BlockDisplay> tagged(NamespacedKey key, String shipId) { return List.of(display); }
    };
  }
  private static final class PairingFailureSurface implements RenderSurface {
    private final RuntimeException failure;

    private PairingFailureSurface(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public Collection<BlockDisplay> tagged(NamespacedKey key, String shipId) {
      throw failure;
    }

    @Override
    public BlockDisplay spawnBlockDisplay(Location location, java.util.function.Consumer<BlockDisplay> config) {
      return null;
    }

    @Override public BlockData blockData(String serialized) { return null; }
    @Override public void teleport(org.bukkit.entity.Entity entity, Location location) {}
    @Override public UUID worldUuid() { return WORLD; }
    @Override public Location location(ShipOrigin origin, double dx, double dy, double dz) {
      return new Location(null, dx, dy, dz);
    }
    @Override public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {}
    @Override public void removeTagged(NamespacedKey key, String shipId) {}
  }

  private static Object defaultValue(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == double.class) return 0.0;
    if (type == float.class) return 0.0f;
    return null;
  }

  private static Ship ship() {
    return new Ship(UUID.randomUUID(), UUID.randomUUID(), new ShipOrigin(WORLD, 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
  }
}
