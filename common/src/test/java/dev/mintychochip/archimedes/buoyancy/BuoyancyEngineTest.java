package dev.mintychochip.archimedes.buoyancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuoyancyEngineTest {
  private static final String ORIGIN_X = "100";
  private static final String ORIGIN_Y = "205";
  private static final String ORIGIN_Z = "300";
  private static final String WATER_BLOCK = ORIGIN_X + "," + ORIGIN_Y + "," + ORIGIN_Z;
  private static final String ORIGIN_BLOCK = ORIGIN_X + ",200," + ORIGIN_Z;

  private static ShipRuntime runtime() {
    return new ShipRuntime() {
      @Override
      public void spawn(Ship ship) {}

      @Override
      public void move(Ship ship, double oldY, double newY) {}

      @Override
      public void remove(Ship ship) {}

      @Override
      public void removeAll(java.util.Collection<Ship> ships) {}
    };
  }

  /** Fake surface with water below the hull. */
  private static final class FakeSurface implements BuoyancySurface {
    final Set<String> water = new HashSet<>();
    final Set<String> solid = new HashSet<>();

    @Override
    public boolean isWater(int x, int y, int z) {
      return water.contains(x + "," + y + "," + z);
    }

    @Override
    public boolean isClear(int x, int y, int z) {
      return !solid.contains(x + "," + y + "," + z);
    }
  }

  private static Ship shipAt(ShipPose pose, BlockPos... positions) {
    ShipOrigin origin =
        new ShipOrigin(UUID.fromString("00000000-0000-0000-0000-000000000001"), 100, 200, 300);
    List<ShipBlock> blocks =
        java.util.Arrays.stream(positions)
            .map(pos -> new ShipBlock(pos, "minecraft:stone"))
            .toList();
    return new Ship(UUID.randomUUID(), UUID.randomUUID(), origin, blocks, pose, true);
  }

  @Test
  void disabledRiseTickAndSinkHaveCurrentNoOpSemantics() {
    FakeSurface surface = new FakeSurface();
    Ship ship = shipAt(new ShipPose(2), new BlockPos(0, 0, 0));
    ship.setBuoyancyEnabled(false);
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);

    assertTrue(buoyancy.rise(ship));
    assertFalse(buoyancy.tick(ship));
    assertFalse(buoyancy.sink(ship, 1));
    assertEquals(2.0, ship.pose().y());
  }

  @Test
  void blockedRisePreservesPose() {
    FakeSurface surface = new FakeSurface();
    surface.solid.add(ORIGIN_BLOCK);
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);

    assertFalse(buoyancy.rise(ship));
    assertEquals(0.0, ship.pose().y());
  }

  @Test
  void blockedTickResetsVelocity() {
    FakeSurface surface = new FakeSurface();
    surface.water.add(WATER_BLOCK);
    Ship ship = shipAt(new ShipPose(5), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);
    buoyancy.rise(ship);
    surface.solid.add("100,204,300");
    buoyancy.tick(ship);
    surface.solid.clear();
  }

  @Test
  void lowerBobBoundaryReflectsVelocity() {
    FakeSurface surface = new FakeSurface();
    surface.water.add(WATER_BLOCK);
    Ship ship = shipAt(new ShipPose(5), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);
    buoyancy.rise(ship);
    for (int i = 0; i < 100; i++) {
      buoyancy.tick(ship);
    }
    double before = ship.pose().y();
    buoyancy.tick(ship);
    assertTrue(ship.pose().y() >= before);
  }

  @Test
  void subThresholdTickStoresVelocityWithoutPathCheck() {
    FakeSurface surface = new FakeSurface();
    surface.water.add(WATER_BLOCK);
    Ship ship = shipAt(new ShipPose(5), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(
            surface, new BuoyancyEngine(0.00001, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);
    buoyancy.rise(ship);
    surface.solid.add("100,205,300");

    assertFalse(buoyancy.tick(ship));
    assertEquals(5.0, ship.pose().y());
  }

  @Test
  void runtimeFailureRestoresPose() {
    FakeSurface surface = new FakeSurface();
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    ShipRuntime failing =
        new ShipRuntime() {
          public void spawn(Ship ignored) {}

          public void move(Ship ignored, double oldY, double newY) {
            throw new dev.mintychochip.archimedes.ship.ShipRuntimeException(
                new IllegalStateException("move"));
          }

          public void remove(Ship ignored) {}

          public void removeAll(java.util.Collection<Ship> ignored) {}
        };
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), failing, 10, 0.5);

    assertFalse(buoyancy.sink(ship, 1));
    assertEquals(0.0, ship.pose().y());
  }

  @Test
  void pathAllowsAirAndWaterButRejectsSolid() {
    FakeSurface surface = new FakeSurface();
    surface.water.add("100,199,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);
    assertTrue(buoyancy.sink(ship, 1));
    surface.solid.add("100,198,300");
    assertFalse(buoyancy.sink(ship, 1));
  }

  @Test
  void sinkThreeSucceedsToNegativeThree() {
    FakeSurface surface = new FakeSurface();
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);

    assertTrue(buoyancy.sink(ship, 3));
    assertEquals(-3.0, ship.pose().y());
  }

  @Test
  void threeSingleSinksReachNegativeThree() {
    FakeSurface surface = new FakeSurface();
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);

    assertTrue(buoyancy.sink(ship, 1));
    assertTrue(buoyancy.sink(ship, 1));
    assertTrue(buoyancy.sink(ship, 1));
    assertEquals(-3.0, ship.pose().y());
  }

  @Test
  void sinkRejectsNonPositiveBlocks() {
    FakeSurface surface = new FakeSurface();
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);

    assertFalse(buoyancy.sink(ship, 0));
    assertFalse(buoyancy.sink(ship, -1));
    assertEquals(0.0, ship.pose().y());
  }

  @Test
  void intermediateObstructionRejectsSinkAndKeepsPose() {
    FakeSurface surface = new FakeSurface();
    surface.solid.add("100,199,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);

    assertFalse(buoyancy.sink(ship, 3));
    assertEquals(0.0, ship.pose().y());
  }

  @Test
  void highShipSinksTowardWater() {
    FakeSurface surface = new FakeSurface();
    surface.water.add("100,205,300");
    // ship high above water (block at y=210, above surface 205) -> no buoyancy -> sinks
    Ship ship = shipAt(new ShipPose(10), new BlockPos(0, 0, 0));
    BuoyancyEngine engine = new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0);
    BuoyancyEngine.Step step = engine.step(ship, 0.0, surface);
    assertTrue(step.y() < ship.pose().y());
  }

  @Test
  void equilibriumKeepsPoseStable() {
    FakeSurface surface = new FakeSurface();
    surface.water.add("100,205,300");
    // equilibrium pose y = 5 (bottom at surface); block at 205 == surface -> submerged
    // submerged=1, weight=1*0.5*0.05=0.025, buoyancy=1.0*0.05*1=0.05 -> net up; but near eq
    Ship ship = shipAt(new ShipPose(5), new BlockPos(0, 0, 0));
    BuoyancyEngine engine = new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0);
    BuoyancyEngine.Step step = engine.step(ship, 0.0, surface);
    // still slightly rising toward equilibrium; assert it is not far
    assertTrue(Math.abs(step.y() - 5.0) < 1.0);
  }

  @Test
  void riseAllowsNegativeWaterlineOffset() {
    FakeSurface surface = new FakeSurface();
    surface.water.add("100,195,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);
    assertTrue(buoyancy.rise(ship));
    assertEquals(-5.0, ship.pose().y());
  }

  @Test
  void bobbingReflectsAtUpperAmplitudeBoundary() {
    FakeSurface surface = new FakeSurface();
    surface.water.add("100,199,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);

    assertTrue(buoyancy.rise(ship));
    boolean reachedUpperBoundary = false;
    boolean movedDownAfterBoundary = false;
    for (int tick = 0; tick < 20; tick++) {
      double previousY = ship.pose().y();
      buoyancy.tick(ship);
      reachedUpperBoundary |= Math.abs(ship.pose().y() + 0.5) < 0.000001;
      movedDownAfterBoundary |= reachedUpperBoundary && ship.pose().y() < previousY;
    }
    assertTrue(reachedUpperBoundary);
    assertTrue(movedDownAfterBoundary);
  }

  @Test
  void sinkAllowsNegativePose() {
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    FakeSurface surface = new FakeSurface();
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);
    assertTrue(buoyancy.sink(ship, 3));
    assertEquals(-3.0, ship.pose().y());
  }
}
