package dev.mintychochip.archimedes.buoyancy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for waterline resolution and submerged volume. */
class BuoyancyResolverTest {
  /** Common water cell used in most tests. */
  private static final String HIGH_WATER_CELL = "100,205,300";

  /** Common low water cell used in the multi-column tests. */
  private static final String LOW_WATER_CELL = "101,203,300";

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
        java.util.Arrays.stream(positions)
            .map(pos -> new ShipBlock(pos, "minecraft:stone"))
            .toList();
    return new Ship(UUID.randomUUID(), UUID.randomUUID(), origin, blocks, pose, true);
  }

  @Test
  void findsWaterSurfaceBelowHull() {
    FakeSurface surface = new FakeSurface();
    // water at y=205 under the hull (origin y=200, block at rel y=0, pose 0)
    surface.water.add(HIGH_WATER_CELL);
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    assertEquals(205, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void equilibriumYPlacesBottomAtWaterSurface() {
    FakeSurface surface = new FakeSurface();
    surface.water.add(HIGH_WATER_CELL);
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    // bottom = origin.y + pose.y + minRelY = 200 + pose.y + 0
    // want bottom = 205 -> pose.y = 5
    assertEquals(5.0, BuoyancyResolver.equilibriumY(ship, surface));
  }

  @Test
  void countsSubmergedBlocksBelowSurface() {
    FakeSurface surface = new FakeSurface();
    surface.water.add(HIGH_WATER_CELL);
    // two-block column; pose 3 -> blocks at y 203, 204; surface 205 -> both submerged
    Ship ship = shipAt(new ShipPose(3), new BlockPos(0, 0, 0), new BlockPos(0, 1, 0));
    assertEquals(2, BuoyancyResolver.submergedVolume(ship, surface));
  }

  @Test
  void returnsNoWaterWhenSolidBelowHull() {
    FakeSurface surface = new FakeSurface();
    surface.solid.add(HIGH_WATER_CELL);
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    assertEquals(Integer.MIN_VALUE, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void returnsHighestWaterInColumn() {
    FakeSurface surface = new FakeSurface();
    surface.water.add("100,203,300");
    surface.water.add(HIGH_WATER_CELL);
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));
    assertEquals(205, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void countsSubmergedPerColumnNotGlobally() {
    FakeSurface surface = new FakeSurface();
    surface.water.add(HIGH_WATER_CELL);
    // column X=100 has surface at y=205 (water at 205, block at rel y=0 -> abs 200, submerged)
    // column X=101 has only water at y=203 (surface 203); a block at rel y=4 -> abs 204 is
    // above that column's surface even though it is below the other column's surface.
    surface.water.add("101,203,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0), new BlockPos(1, 4, 0));
    // column 100: block abs 200 <= 205 -> submerged. column 101: block abs 204 > 203 -> not.
    assertEquals(1, BuoyancyResolver.submergedVolume(ship, surface));
  }

  @Test
  void usesShallowestWaterAcrossColumns() {
    FakeSurface surface = new FakeSurface();
    surface.water.add(HIGH_WATER_CELL);
    surface.water.add("101,203,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
    assertEquals(203, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void fractionalNegativeAnchorDyUsesShiftedSamplingWindow() {
    FakeSurface surface = new FakeSurface();
    // anchorDy=floor(-0.5)=-1: bottom=199, so the scan includes y=135.
    surface.water.add("100,135,300");
    Ship ship = shipAt(new ShipPose(-0.5), new BlockPos(0, 0, 0));

    assertEquals(135, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void shiftedSamplingWindowUsesEachBlocksDistinctOffset() {
    FakeSurface surface = new FakeSurface();
    // With anchorDy=-1, the two bottoms are 199 and 200, whose lower scan
    // boundaries are y=135 and y=136 respectively.
    surface.water.add("100,135,300");
    surface.water.add("101,136,300");
    Ship ship = shipAt(new ShipPose(-0.5), new BlockPos(0, 0, 0), new BlockPos(1, 1, 0));

    assertEquals(135, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void sealedColumnIgnoresWaterBelowSolid() {
    FakeSurface surface = new FakeSurface();
    surface.solid.add("100,205,300");
    surface.water.add("100,204,300");
    Ship ship = shipAt(new ShipPose(0), new BlockPos(0, 0, 0));

    assertEquals(BuoyancyResolver.NO_WATER, BuoyancyResolver.waterSurfaceY(ship, surface));
  }

  @Test
  void noWaterEquilibriumIsZero() {
    Ship ship = shipAt(new ShipPose(7), new BlockPos(0, 0, 0));

    assertEquals(0.0, BuoyancyResolver.equilibriumY(ship, new FakeSurface()));
  }
}
