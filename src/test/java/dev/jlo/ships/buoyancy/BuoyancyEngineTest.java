package dev.jlo.ships.buoyancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.ships.deck.DeckSurface;
import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import dev.jlo.ships.model.ShipPose;
import dev.jlo.ships.ship.ShipHolder;
import dev.jlo.ships.ship.ShipRendererLike;
import dev.jlo.ships.ship.ShipRuntime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuoyancyEngineTest {
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
    ShipRendererLike renderer =
        new ShipRendererLike() {
          @Override
          public void render(Ship ignored, ShipHolder holder) {}

          @Override
          public void removeRuntime(Ship ignored) {}

          @Override
          public void reposition(Ship ignored, double oldY, double newY) {}
        };
    DeckSurface deckSurface =
        new DeckSurface() {
          @Override
          public boolean canPlace(int x, int y, int z) {
            return true;
          }

          @Override
          public boolean isClear(int x, int y, int z) {
            return true;
          }

          @Override
          public boolean placeBarrier(int x, int y, int z) {
            return true;
          }

          @Override
          public void removeBarrier(int x, int y, int z) {}
        };
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);
    assertTrue(buoyancy.rise(ship));
    assertEquals(-5.0, ship.pose().y());
  }

  @Test
  void sinkAllowsNegativePose() {
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    FakeSurface surface = new FakeSurface();
    ShipRendererLike renderer =
        new ShipRendererLike() {
          @Override
          public void render(Ship ignored, ShipHolder holder) {}

          @Override
          public void removeRuntime(Ship ignored) {}

          @Override
          public void reposition(Ship ignored, double oldY, double newY) {}
        };
    DeckSurface deckSurface =
        new DeckSurface() {
          @Override
          public boolean canPlace(int x, int y, int z) {
            return true;
          }

          @Override
          public boolean isClear(int x, int y, int z) {
            return true;
          }

          @Override
          public boolean placeBarrier(int x, int y, int z) {
            return true;
          }

          @Override
          public void removeBarrier(int x, int y, int z) {}
        };
    BuoyancyImpl buoyancy =
        new BuoyancyImpl(surface, new BuoyancyEngine(0.05, 1.0, 0.5, 0.9, 1.0), runtime(), 10, 0.5);
    assertTrue(buoyancy.sink(ship, 3));
    assertEquals(-3.0, ship.pose().y());
  }
}
