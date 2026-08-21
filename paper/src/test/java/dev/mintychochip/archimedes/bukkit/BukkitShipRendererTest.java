package dev.mintychochip.archimedes.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipTransform;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.render.RenderSurface;
import dev.mintychochip.archimedes.sail.SailMesh;
import dev.mintychochip.archimedes.sail.SailPiece;
import dev.mintychochip.archimedes.ship.ShipRuntimeException;
import dev.mintychochip.phys.FlowField;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class BukkitShipRendererTest {
  private static final String NAMESPACE = "archimedes";
  private static final String KEY = "test";
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
              Vehicle ship = ship();
              new BukkitShipRenderer(surface, new NamespacedKey(NAMESPACE, KEY))
                  .render(
                      ship,
                      ignored -> {
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
    BukkitShipRenderer renderer =
        new BukkitShipRenderer(surface, new NamespacedKey(NAMESPACE, KEY));
    Vehicle ship = ship();
    renderer.render(ship, ignored -> {});

    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> renderer.reposition(ship, 0, 1));
    assertSame(teleport, thrown.getCause());
    assertTrue(thrown.getMessage().contains(ship.id().toString()));
  }

  @Test
  void removeRuntimeNormalizesFailureWithShipContext() {
    RuntimeException failure = new IllegalStateException("remove");
    BukkitShipRenderer renderer =
        new BukkitShipRenderer(
            surfaceThatFails(new RuntimeException("unused"), failure),
            new NamespacedKey(NAMESPACE, KEY));
    Vehicle ship = ship();

    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> renderer.removeRuntime(ship));

    assertSame(failure, thrown.getCause());
    assertTrue(thrown.getMessage().contains(ship.id().toString()));
  }

  @Test
  void displaysAreHiddenByDefaultAndShownOnlyWithLos() {
    VisibilitySurface surface = new VisibilitySurface();
    UUID west = UUID.randomUUID();
    UUID east = UUID.randomUUID();
    surface.viewers.add(new RenderSurface.Viewer(west, -0.5, 0.5, 0.5));
    surface.viewers.add(new RenderSurface.Viewer(east, 3.5, 0.5, 0.5));
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(WORLD, 0, 0, 0),
            List.of(
                new ShipBlock(new BlockPos(1, 0, 0), "minecraft:stone"),
                new ShipBlock(new BlockPos(2, 0, 0), "minecraft:stone")));
    new BukkitShipRenderer(surface, new NamespacedKey(NAMESPACE, KEY)).render(ship, ignored -> {});
    assertEquals(false, surface.fakes.get(0).visibleByDefault);
    assertEquals(false, surface.fakes.get(1).visibleByDefault);
    assertTrue(surface.shown.contains(west + ":1,0,0"));
    assertTrue(surface.hidden.contains(west + ":2,0,0"));
    assertTrue(surface.shown.contains(east + ":2,0,0"));
    assertTrue(surface.hidden.contains(east + ":1,0,0"));
  }

  @Test
  void billowedSailPlateWithClearLosIsShown() {
    VisibilitySurface surface = new VisibilitySurface();
    UUID viewer = UUID.randomUUID();
    surface.viewers.add(new RenderSurface.Viewer(viewer, 2.5, 2.5, 4.0));
    FlowField wind = FlowField.uniform(new Vector3d(0, 0, 8));
    List<ShipBlock> blocks = new ArrayList<>();
    for (int x = 0; x < 5; x++) {
      for (int y = 0; y < 5; y++) {
        blocks.add(new ShipBlock(new BlockPos(x, y, 0), "minecraft:white_wool"));
      }
    }
    Vehicle ship =
        new Vehicle(UUID.randomUUID(), UUID.randomUUID(), new ShipOrigin(WORLD, 0, 0, 0), blocks);
    List<SailPiece> pieces = SailMesh.tessellate(SailMesh.cellsOf(ship.intactBlocks()), wind);
    Set<BlockPos> occupied = new HashSet<>();
    for (ShipBlock block : ship.blocks()) {
      occupied.add(ShipTransform.cell(ship, block.pos()));
    }
    boolean offOccupied = false;
    for (int i = 0; i < pieces.size(); i++) {
      SailPiece piece = pieces.get(i);
      BlockPos cell =
          new BlockPos(
              (int) Math.floor(ship.origin().x() + ship.pose().x() + piece.originX()),
              (int) Math.floor(ship.origin().y() + ship.pose().y() + piece.originY()),
              (int) Math.floor(ship.origin().z() + ship.pose().z() + piece.originZ()));
      if (!occupied.contains(cell)) {
        offOccupied = true;
        break;
      }
    }
    assertTrue(offOccupied, "wind must shift a plate origin off occupied cells");
    new BukkitShipRenderer(surface, new NamespacedKey(NAMESPACE, KEY), wind)
        .render(ship, ignored -> {});
    for (int i = 0; i < pieces.size(); i++) {
      SailPiece piece = pieces.get(i);
      BlockPos cell =
          new BlockPos(
              (int) Math.floor(ship.origin().x() + ship.pose().x() + piece.originX()),
              (int) Math.floor(ship.origin().y() + ship.pose().y() + piece.originY()),
              (int) Math.floor(ship.origin().z() + ship.pose().z() + piece.originZ()));
      if (!occupied.contains(cell)) {
        assertTrue(
            surface.shown.contains(viewer + ":sail:" + i),
            "billowed plate " + i + " cell=" + cell.x() + "," + cell.y() + "," + cell.z());
        assertFalse(surface.hidden.contains(viewer + ":sail:" + i));
      }
    }
  }

  @Test
  void clothRagdollIsABlockDisplayThatReceivesOrientation() {
    RecordingSurface surface = new RecordingSurface();
    BukkitShipRenderer renderer =
        new BukkitShipRenderer(surface, new NamespacedKey(NAMESPACE, KEY));
    Vehicle ship = ship();
    UUID debris = UUID.randomUUID();
    renderer.spawnClothRagdoll(ship, debris, "minecraft:white_wool", 1, 2, 3);
    assertEquals(1, surface.spawned.size());
    renderer.moveClothRagdoll(debris, 4, 5, 6, 0, 0, 0, 1);
    assertEquals(4.0, surface.fakes.get(0).location.getX(), 1e-6);
    assertEquals(5.0, surface.fakes.get(0).location.getY(), 1e-6);
    assertEquals(6.0, surface.fakes.get(0).location.getZ(), 1e-6);
    assertNotNull(surface.fakes.get(0).transformation);
  }

  @Test
  void repositionNormalizesPairingRuntimeFailureWithShipContext() {
    RuntimeException pairing = new IllegalStateException("pairing");
    RenderSurface surface = new PairingFailureSurface(pairing);
    BukkitShipRenderer renderer =
        new BukkitShipRenderer(surface, new NamespacedKey(NAMESPACE, KEY));

    ShipRuntimeException thrown =
        assertThrows(ShipRuntimeException.class, () -> renderer.reposition(ship(), 0, 1));

    assertSame(pairing, thrown.getCause());
    assertTrue(thrown.getMessage().contains("Renderer reposition failed"));
  }

  private static RenderSurface surfaceThatFails(RuntimeException... failures) {
    return new RenderSurface() {
      private final BlockDisplay display =
          (BlockDisplay)
              Proxy.newProxyInstance(
                  BlockDisplay.class.getClassLoader(),
                  new Class<?>[] {BlockDisplay.class},
                  (proxy, method, args) -> {
                    if (method.getName().equals("getPersistentDataContainer")) {
                      return Proxy.newProxyInstance(
                          getClass().getClassLoader(),
                          new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                          (container, containerMethod, containerArgs) -> {
                            if ("set".equals(containerMethod.getName())) {
                              return null;
                            }
                            if ("get".equals(containerMethod.getName())) {
                              return "0,0,0";
                            }
                            return defaultValue(containerMethod.getReturnType());
                          });
                    }
                    if (method.getName().equals("getLocation")) {
                      return new Location(null, 0, 0, 0);
                    }
                    return defaultValue(method.getReturnType());
                  });

      @Override
      public BlockDisplay spawnBlockDisplay(
          Location location, java.util.function.Consumer<BlockDisplay> config) {
        config.accept(display);
        return display;
      }

      @Override
      public BlockData blockData(String serialized) {
        return null;
      }

      @Override
      public void teleport(org.bukkit.entity.Entity entity, Location location) {
        if (failures.length > 1) {
          throw failures[1];
        }
      }

      @Override
      public UUID worldUuid() {
        return WORLD;
      }

      @Override
      public Location location(ShipOrigin origin, double dx, double dy, double dz) {
        return new Location(null, dx, dy, dz);
      }

      @Override
      public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {}

      @Override
      public void removeTagged(NamespacedKey key, String shipId) {
        if (failures.length > 1) {
          throw failures[1];
        }
      }

      @Override
      public Collection<BlockDisplay> tagged(NamespacedKey key, String shipId) {
        return List.of(display);
      }
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
    public BlockDisplay spawnBlockDisplay(
        Location location, java.util.function.Consumer<BlockDisplay> config) {
      return null;
    }

    @Override
    public BlockData blockData(String serialized) {
      return null;
    }

    @Override
    public void teleport(org.bukkit.entity.Entity entity, Location location) {}

    @Override
    public UUID worldUuid() {
      return WORLD;
    }

    @Override
    public Location location(ShipOrigin origin, double dx, double dy, double dz) {
      return new Location(null, dx, dy, dz);
    }

    @Override
    public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {}

    @Override
    public void removeTagged(NamespacedKey key, String shipId) {}
  }

  private static Object defaultValue(Class<?> type) {
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

  private static class RecordingSurface implements RenderSurface {
    final List<BlockDisplay> spawned = new ArrayList<>();
    final List<FakeRagdoll> fakes = new ArrayList<>();

    @Override
    public BlockDisplay spawnBlockDisplay(
        Location location, java.util.function.Consumer<BlockDisplay> config) {
      FakeRagdoll fake = new FakeRagdoll();
      fake.location = location;
      BlockDisplay proxy = fake.proxy();
      config.accept(proxy);
      fakes.add(fake);
      spawned.add(proxy);
      return proxy;
    }

    @Override
    public BlockData blockData(String serialized) {
      return (BlockData)
          Proxy.newProxyInstance(
              BlockData.class.getClassLoader(),
              new Class<?>[] {BlockData.class},
              (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    @Override
    public Collection<BlockDisplay> tagged(NamespacedKey key, String shipId) {
      List<BlockDisplay> found = new ArrayList<>();
      String want = key.getNamespace() + ":" + key.getKey();
      for (int i = 0; i < spawned.size(); i++) {
        for (Map.Entry<NamespacedKey, String> tag : fakes.get(i).tags.entrySet()) {
          String have = tag.getKey().getNamespace() + ":" + tag.getKey().getKey();
          if (want.equals(have) && shipId.equals(tag.getValue())) {
            found.add(spawned.get(i));
          }
        }
      }
      return found;
    }

    @Override
    public void teleport(org.bukkit.entity.Entity entity, Location location) {
      for (int i = 0; i < spawned.size(); i++) {
        if (spawned.get(i) == entity) {
          fakes.get(i).location = location;
          break;
        }
      }
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
    public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {}

    @Override
    public void removeTagged(NamespacedKey key, String shipId) {}
  }

  private static final class FakeRagdoll {
    Location location;
    Transformation transformation;
    boolean visibleByDefault = true;
    final Map<NamespacedKey, String> tags = new HashMap<>();

    BlockDisplay proxy() {
      return (BlockDisplay)
          Proxy.newProxyInstance(
              BlockDisplay.class.getClassLoader(),
              new Class<?>[] {BlockDisplay.class},
              (target, method, args) -> {
                switch (method.getName()) {
                  case "setVisibleByDefault":
                    visibleByDefault = (Boolean) args[0];
                    return null;
                  case "setBlock":
                  case "setPersistent":
                  case "setTeleportDuration":
                  case "remove":
                    return null;
                  case "isDead":
                    return false;
                  case "getLocation":
                    return location;
                  case "setTransformation":
                    transformation = (Transformation) args[0];
                    return null;
                  case "getTransformation":
                    return transformation;
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
                            NamespacedKey wanted = (NamespacedKey) containerArgs[0];
                            String want = wanted.getNamespace() + ":" + wanted.getKey();
                            for (Map.Entry<NamespacedKey, String> tag : tags.entrySet()) {
                              String have =
                                  tag.getKey().getNamespace() + ":" + tag.getKey().getKey();
                              if (want.equals(have)) {
                                return tag.getValue();
                              }
                            }
                            return null;
                          }
                          return defaultValue(containerMethod.getReturnType());
                        });
                  default:
                    return defaultValue(method.getReturnType());
                }
              });
    }
  }

  private static final class VisibilitySurface extends RecordingSurface {
    final List<RenderSurface.Viewer> viewers = new ArrayList<>();
    final java.util.Set<String> shown = new java.util.HashSet<>();
    final java.util.Set<String> hidden = new java.util.HashSet<>();

    @Override
    public Collection<RenderSurface.Viewer> viewers() {
      return viewers;
    }

    @Override
    public void showTo(UUID viewerId, org.bukkit.entity.Entity entity) {
      record(shown, viewerId, entity);
    }

    @Override
    public void hideFrom(UUID viewerId, org.bukkit.entity.Entity entity) {
      record(hidden, viewerId, entity);
    }

    private void record(
        java.util.Set<String> sink, UUID viewerId, org.bukkit.entity.Entity entity) {
      int index = -1;
      for (int i = 0; i < spawned.size(); i++) {
        if (spawned.get(i) == entity) {
          index = i;
          break;
        }
      }
      if (index < 0) {
        return;
      }
      String cell = null;
      String sail = null;
      for (Map.Entry<NamespacedKey, String> tag : fakes.get(index).tags.entrySet()) {
        if (tag.getKey().getKey().endsWith("-block")) {
          cell = tag.getValue();
        }
        if (tag.getKey().getKey().endsWith("-sail")) {
          sail = tag.getValue();
        }
      }
      if (sail != null) {
        sink.add(viewerId + ":sail:" + sail);
      } else {
        sink.add(viewerId + ":" + cell);
      }
    }
  }

  private static Vehicle ship() {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(WORLD, 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")));
  }
}
