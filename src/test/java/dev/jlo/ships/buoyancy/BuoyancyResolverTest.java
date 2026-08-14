package dev.jlo.ships.buoyancy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import dev.jlo.ships.model.ShipPose;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for waterline resolution and submerged volume. */
class BuoyancyResolverTest {
  /** Fake surface backed by a set of water and solid cells. */
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
        java.util.Arrays.stream(positions).map(pos -> new ShipBlock(pos, "minecraft:stone")).toList();
    return new Ship(UUID.randomUUID(), UUID.randomUUID(), origin, blocks, pose, true);
  }

  @Test
  void findsWaterSurfaceBelowHull() {
    FakeSurface surface = new FakeSurface();
    // water at y=205 under the hull (origin y=200, block at rel y=0, pose 0)
    surface.water.add("100,205,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    assertEquals(205, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void equilibriumYPlacesBottomAtWaterSurface() {
    FakeSurface surface = new FakeSurface();
    surface.water.add("100,205,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    // bottom = origin.y + pose.y + minRelY = 200 + pose.y + 0
    // want bottom = 205 -> pose.y = 5
    assertEquals(5.0, BuoyancyResolver.equilibriumY(ship, surface));
  }

  @Test
  void countsSubmergedBlocksBelowSurface() {
    FakeSurface surface = new FakeSurface();
    surface.water.add("100,205,300");
    // two-block column; pose 3 -> blocks at y 203, 204; surface 205 -> both submerged
    Ship ship = shipAt(new ShipPose(3), new BlockPos(0, 0, 0), new BlockPos(0, 1, 0));
    assertEquals(2, BuoyancyResolver.submergedVolume(ship, surface));
  }

  @Test
  void returnsNoWaterWhenSolidBelowHull() {
    FakeSurface surface = new FakeSurface();
    surface.solid.add("100,205,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    assertEquals(Integer.MIN_VALUE, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void returnsHighestWaterInColumn() {
    FakeSurface surface = new FakeSurface();
    // water at y=203 and y=205; the effective surface is the highest, 205
    surface.water.add("100,203,300");
    surface.water.add("100,205,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    assertEquals(205, BuoyancyResolver.waterSurfaceY(ship, surface));
  }
}
