package dev.mintychochip.archimedes.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.ShipTransform;
import dev.mintychochip.archimedes.ship.ShipRuntimeException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

/** Behavior tests for the block-display ship renderer. */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ShipRendererTest {
  private static final String NAMESPACE = "archimedes"; // shared namespace for test keys

  /** Common capturable material. */
  @SuppressWarnings("PMD.AvoidDuplicateLiterals")
  private static final String STONE = "minecraft:stone";

  /** Cloth material used for tessellated sail pieces. */
  private static final String WHITE_WOOL = "minecraft:white_wool";

  /** Common world identifier. */
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** An in-memory fake implementing the display contract via proxy. */
  private static final class FakeDisplay {
    BlockData block;
    boolean persistent = true;
    Location location;
    Transformation transformation;
    int teleportDuration;
    final List<Integer> teleportDurations = new ArrayList<>();
    final Map<NamespacedKey, String> tags = new HashMap<>();
    final List<String> invoked = new ArrayList<>();

    BlockDisplay proxy() {
      return (BlockDisplay)
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {BlockDisplay.class},
              (target, method, args) -> {
                invoked.add(method.getName());
                switch (method.getName()) {
                  case "getBlock":
                    return block;
                  case "setBlock":
                    block = (BlockData) args[0];
                    return null;
                  case "getTransformation":
                    return transformation;
                  case "setTransformation":
                    transformation = (Transformation) args[0];
                    return null;
                  case "getTeleportDuration":
                    return teleportDuration;
                  case "setTeleportDuration":
                    teleportDuration = (Integer) args[0];
                    teleportDurations.add((Integer) args[0]);
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
    final List<FakeDisplay> fakes = new ArrayList<>();

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
      fakes.add(fake);
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
      List<BlockDisplay> found = new ArrayList<>();
      for (int i = 0; i < spawned.size(); i++) {
        if (shipId.equals(fakes.get(i).tags.get(key))) {
          found.add(spawned.get(i));
        }
      }
      java.util.Collections.reverse(found);
      return found;
    }

    @Override
    public void teleport(org.bukkit.entity.Entity entity, Location location) {
      teleports.add(location);
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
    public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {
      renderedShips.add(shipId);
    }

    @Override
    public void removeTagged(NamespacedKey key, String shipId) {
      for (int i = spawned.size() - 1; i >= 0; i--) {
        if (shipId.equals(fakes.get(i).tags.get(key))) {
          spawned.remove(i);
          fakes.remove(i);
        }
      }
    }
  }

  @Test
  void rendererRemovalContinuesToSecondDisplayAndSuppressesItsFailure() {
    RuntimeException first = new IllegalStateException("first display");
    RuntimeException second = new IllegalArgumentException("second display");
    List<Integer> attempts = new ArrayList<>();
    BlockDisplay firstDisplay = removalDisplay(first, attempts);
    BlockDisplay secondDisplay = removalDisplay(second, attempts);
    WorldProxy world = new WorldProxy(List.of(firstDisplay, secondDisplay));
    RenderSurface surface = RenderSurface.of(world.proxy());
    ShipRuntimeException thrown =
        assertThrows(
            ShipRuntimeException.class,
            () -> surface.removeTagged(new NamespacedKey(NAMESPACE, "ship"), "ship-id"));
    assertEquals(2, attempts.size());
    assertEquals(1, thrown.getSuppressed().length);
  }

  private static BlockDisplay removalDisplay(RuntimeException failure, List<Integer> attempts) {
    return (BlockDisplay)
        Proxy.newProxyInstance(
            BlockDisplay.class.getClassLoader(),
            new Class<?>[] {BlockDisplay.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getPersistentDataContainer")) {
                return Proxy.newProxyInstance(
                    ShipRendererTest.class.getClassLoader(),
                    new Class<?>[] {org.bukkit.persistence.PersistentDataContainer.class},
                    (container, containerMethod, containerArgs) -> {
                      if ("get".equals(containerMethod.getName())) return "ship-id";
                      return defaultFor(containerMethod.getReturnType());
                    });
              }
              if (method.getName().equals("remove")) {
                attempts.add(1);
                throw failure;
              }
              return defaultFor(method.getReturnType());
            });
  }

  private static final class WorldProxy {
    private final List<BlockDisplay> displays;

    private WorldProxy(List<BlockDisplay> displays) {
      this.displays = displays;
    }

    private World proxy() {
      return (World)
          Proxy.newProxyInstance(
              World.class.getClassLoader(),
              new Class<?>[] {World.class},
              (proxy, method, args) -> {
                if (method.getName().equals("getEntitiesByClass")) return displays;
                return defaultFor(method.getReturnType());
              });
    }
  }

  @Test
  void renderNormalizesRuntimeFailureWithShipIdAndCause() {
    Ship ship = shipWithBlock(0, 0, 0, STONE);
    RuntimeException cause = new IllegalStateException("render boom");
    SpySurface surface = new SpySurface();
    surface.renderFailure = cause;
    dev.mintychochip.archimedes.ship.ShipRuntimeException failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            dev.mintychochip.archimedes.ship.ShipRuntimeException.class,
            () ->
                new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(
                        surface, new NamespacedKey("archimedes", "test"))
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
  void renderUsesOneUntransformedBlockDisplayPerCapturedBlock() {
    SpySurface surface = new SpySurface();
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(
                new ShipBlock(new BlockPos(0, 0, 0), STONE),
                new ShipBlock(new BlockPos(1, 0, 0), STONE)),
            new ShipPose(0.5),
            true);

    new ShipRenderer().render(ship, surface);

    assertEquals(2, surface.spawned.size());
    assertEquals(2, surface.fakes.size());
    assertEquals(
        ShipTransform.visual(ship, new BlockPos(0, 0, 0)).x(),
        surface.spawned.get(0).getLocation().getX(),
        0.001);
    assertEquals(
        ShipTransform.visual(ship, new BlockPos(1, 0, 0)).x(),
        surface.spawned.get(1).getLocation().getX(),
        0.001);
    for (FakeDisplay fake : surface.fakes) {
      assertTrue(fake.invoked.contains("setBlock"));
      assertFalse(fake.invoked.contains("setTransformation"));
      assertFalse(fake.invoked.contains("setTransformationMatrix"));
    }
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
    dev.mintychochip.archimedes.bukkit.BukkitShipRenderer renderer =
        new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(
            surface, new NamespacedKey("archimedes", "test"));
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
    dev.mintychochip.archimedes.bukkit.BukkitShipRenderer renderer =
        new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(
            surface, new NamespacedKey("archimedes", "test"));
    renderer.render(ship, ignored -> {});
    ship.setPose(new ShipPose(2.0));
    renderer.reposition(ship, 0.0, 2.0);
    java.util.Set<Double> repositionedX =
        java.util.Set.of(surface.teleports.get(2).getX(), surface.teleports.get(3).getX());
    assertEquals(java.util.Set.of(101.0, 109.0), repositionedX);
  }

  @Test
  void stoneOnlyShipStillSpawnsOneUntransformedDisplayPerBlock() {
    SpySurface surface = new SpySurface();
    surface.dataById.put(STONE, markerData(STONE));
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(
                new ShipBlock(new BlockPos(0, 0, 0), STONE),
                new ShipBlock(new BlockPos(1, 0, 0), STONE)),
            new ShipPose(0.5),
            true);
    NamespacedKey shipKey = new NamespacedKey(NAMESPACE, "test");
    new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(surface, shipKey)
        .render(ship, ignored -> {});

    assertEquals(2, surface.spawned.size());
    for (FakeDisplay fake : surface.fakes) {
      assertEquals(surface.dataById.get(STONE), fake.block);
      assertTrue(fake.invoked.contains("setBlock"));
      assertFalse(fake.invoked.contains("setTransformation"));
      assertFalse(fake.invoked.contains("setTransformationMatrix"));
    }
  }

  @Test
  void clothRegionSpawnsTransformedPlatesInsteadOfUntransformedCubes() {
    SpySurface surface = new SpySurface();
    surface.dataById.put(STONE, markerData(STONE));
    surface.dataById.put(WHITE_WOOL, markerData(WHITE_WOOL));
    Ship ship = stoneAndWoolWall();
    NamespacedKey shipKey = new NamespacedKey(NAMESPACE, "test");
    new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(surface, shipKey)
        .render(ship, ignored -> {});

    List<FakeDisplay> hull = new ArrayList<>();
    List<FakeDisplay> sails = new ArrayList<>();
    for (FakeDisplay fake : surface.fakes) {
      if (fake.invoked.contains("setTransformation")
          || fake.invoked.contains("setTransformationMatrix")) {
        sails.add(fake);
      } else {
        hull.add(fake);
      }
    }
    assertEquals(1, hull.size(), "non-cloth hull stays one untransformed cube");
    assertEquals(surface.dataById.get(STONE), hull.get(0).block);
    assertFalse(hull.get(0).invoked.contains("setTransformation"));
    assertTrue(sails.size() > 1, "multi-cell cloth must spawn a series of plates");
    assertNotEquals(3, untransformedClothCount(surface), "cloth must not be one cube per cell");
    for (FakeDisplay sail : sails) {
      assertEquals(surface.dataById.get(WHITE_WOOL), sail.block);
      assertTrue(sail.invoked.contains("setBlock"));
      assertNotNull(sail.transformation);
      org.joml.Vector3f scale = sail.transformation.getScale();
      boolean thin = scale.x != 1.0f || scale.y != 1.0f || scale.z != 1.0f;
      org.joml.Quaternionf rot = sail.transformation.getLeftRotation();
      boolean rotated = rot.x != 0.0f || rot.y != 0.0f || rot.z != 0.0f || rot.w != 1.0f;
      assertTrue(thin || rotated, "sail piece must be a transformed plate");
    }
  }

  @Test
  void sailPiecesMoveWithRepositionAndVanishOnRemove() {
    SpySurface surface = new SpySurface();
    surface.dataById.put(STONE, markerData(STONE));
    surface.dataById.put(WHITE_WOOL, markerData(WHITE_WOOL));
    Ship ship = stoneAndWoolWall();
    NamespacedKey shipKey = new NamespacedKey(NAMESPACE, "test");
    dev.mintychochip.archimedes.bukkit.BukkitShipRenderer renderer =
        new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(surface, shipKey);
    renderer.render(ship, ignored -> {});

    List<FakeDisplay> sails = transformed(surface);
    assertTrue(sails.size() > 1);
    List<Double> yBefore = new ArrayList<>();
    for (FakeDisplay sail : sails) {
      yBefore.add(sail.location.getY());
    }

    ship.setPose(new ShipPose(3.0));
    renderer.reposition(ship, 0.0, 3.0);

    List<FakeDisplay> moved = transformed(surface);
    assertEquals(sails.size(), moved.size());
    for (int i = 0; i < moved.size(); i++) {
      assertEquals(yBefore.get(i) + 3.0, moved.get(i).location.getY(), 0.001);
    }

    renderer.removeRuntime(ship);
    assertTrue(surface.tagged(shipKey, ship.id().toString()).isEmpty());
    assertEquals(0, transformed(surface).size());
    assertEquals(0, surface.spawned.size());
  }

  @Test
  void stoneShipVisualsGetNonZeroTeleportDurationAndStayUntransformed() {
    SpySurface surface = new SpySurface();
    surface.dataById.put(STONE, markerData(STONE));
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(
                new ShipBlock(new BlockPos(0, 0, 0), STONE),
                new ShipBlock(new BlockPos(1, 0, 0), STONE)),
            new ShipPose(0.5),
            true);
    new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(
            surface, new NamespacedKey(NAMESPACE, "test"))
        .render(ship, ignored -> {});

    assertEquals(2, surface.fakes.size());
    for (FakeDisplay fake : surface.fakes) {
      assertTrue(fake.invoked.contains("setBlock"));
      assertFalse(fake.invoked.contains("setTransformation"));
      assertFalse(fake.invoked.contains("setTransformationMatrix"));
      assertTrue(fake.invoked.contains("setTeleportDuration"));
      assertTrue(fake.teleportDuration >= 1, "client must interpolate visual teleports");
    }
  }

  @Test
  void clothShipVisualsGetTeleportDurationAndKeepPlateTransforms() {
    SpySurface surface = new SpySurface();
    surface.dataById.put(STONE, markerData(STONE));
    surface.dataById.put(WHITE_WOOL, markerData(WHITE_WOOL));
    new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(
            surface, new NamespacedKey(NAMESPACE, "test"))
        .render(stoneAndWoolWall(), ignored -> {});

    List<FakeDisplay> hull = new ArrayList<>();
    List<FakeDisplay> sails = new ArrayList<>();
    for (FakeDisplay fake : surface.fakes) {
      assertTrue(fake.invoked.contains("setTeleportDuration"));
      assertTrue(fake.teleportDuration >= 1);
      if (fake.invoked.contains("setTransformation")
          || fake.invoked.contains("setTransformationMatrix")) {
        sails.add(fake);
      } else {
        hull.add(fake);
      }
    }
    assertEquals(1, hull.size());
    assertFalse(hull.get(0).invoked.contains("setTransformation"));
    assertEquals(surface.dataById.get(STONE), hull.get(0).block);
    assertTrue(sails.size() > 1);
    for (FakeDisplay sail : sails) {
      assertEquals(surface.dataById.get(WHITE_WOOL), sail.block);
      assertNotNull(sail.transformation);
    }
  }

  @Test
  void repositionKeepsModelCornersAndDoesNotClearTeleportDuration() {
    SpySurface surface = new SpySurface();
    surface.dataById.put(STONE, markerData(STONE));
    surface.dataById.put(WHITE_WOOL, markerData(WHITE_WOOL));
    Ship ship = stoneAndWoolWall();
    NamespacedKey shipKey = new NamespacedKey(NAMESPACE, "test");
    dev.mintychochip.archimedes.bukkit.BukkitShipRenderer renderer =
        new dev.mintychochip.archimedes.bukkit.BukkitShipRenderer(surface, shipKey);
    renderer.render(ship, ignored -> {});
    for (FakeDisplay fake : surface.fakes) {
      assertTrue(fake.teleportDuration >= 1);
    }

    ship.setPose(new ShipPose(3.0));
    renderer.reposition(ship, 0.0, 3.0);

    ShipTransform.VisualPosition hull = ShipTransform.visual(ship, new BlockPos(0, 0, 0));
    assertEquals(hull.x(), surface.fakes.get(0).location.getX(), 0.001);
    assertEquals(hull.y(), surface.fakes.get(0).location.getY(), 0.001);
    assertEquals(hull.z(), surface.fakes.get(0).location.getZ(), 0.001);
    for (FakeDisplay fake : surface.fakes) {
      assertTrue(fake.teleportDuration >= 1, "reposition must not snap duration back to 0");
      assertFalse(fake.teleportDurations.contains(0));
    }
  }

  private static Ship stoneAndWoolWall() {
    return new Ship(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(WORLD, 100, 200, 300),
        List.of(
            new ShipBlock(new BlockPos(0, 0, 0), STONE),
            new ShipBlock(new BlockPos(1, 0, 0), WHITE_WOOL),
            new ShipBlock(new BlockPos(2, 0, 0), WHITE_WOOL),
            new ShipBlock(new BlockPos(3, 0, 0), WHITE_WOOL)),
        new ShipPose(0.0),
        true);
  }

  private static List<FakeDisplay> transformed(SpySurface surface) {
    List<FakeDisplay> sails = new ArrayList<>();
    for (FakeDisplay fake : surface.fakes) {
      if (fake.invoked.contains("setTransformation")
          || fake.invoked.contains("setTransformationMatrix")) {
        sails.add(fake);
      }
    }
    return sails;
  }

  private static int untransformedClothCount(SpySurface surface) {
    int count = 0;
    BlockData wool = surface.dataById.get(WHITE_WOOL);
    for (FakeDisplay fake : surface.fakes) {
      if (wool.equals(fake.block)
          && !fake.invoked.contains("setTransformation")
          && !fake.invoked.contains("setTransformationMatrix")) {
        count++;
      }
    }
    return count;
  }

  private static BlockData markerData(String id) {
    return (BlockData)
        Proxy.newProxyInstance(
            BlockData.class.getClassLoader(),
            new Class<?>[] {BlockData.class},
            (proxy, method, args) -> {
              if ("toString".equals(method.getName()) || "getAsString".equals(method.getName())) {
                return id;
              }
              if ("equals".equals(method.getName())) {
                return proxy == args[0];
              }
              if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
              }
              return defaultFor(method.getReturnType());
            });
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
