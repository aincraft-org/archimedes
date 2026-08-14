package dev.jlo.ships.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavior tests for the six-directional assembly scanner. */
class ShipScannerTest {
  /** Common forbidden water material. */
  private static final String WATER = "minecraft:water";

  /** Common capturable material. */
  private static final String STONE = "minecraft:stone";

  /** A world reporting a rectangular solid of capturable blocks. */
  private static final class TestWorld implements ScannerWorld {
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final String material;
    private final boolean[] air;

    TestWorld(int sizeX, int sizeY, int sizeZ, String material) {
      this.sizeX = sizeX;
      this.sizeY = sizeY;
      this.sizeZ = sizeZ;
      this.material = material;
      this.air = new boolean[sizeX * sizeY * sizeZ];
    }

    @Override
    public String materialAt(int x, int y, int z) {
      return material;
    }

    @Override
    public boolean airAt(int x, int y, int z) {
      if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
        return true;
      }
      return air[(y * sizeZ + z) * sizeX + x];
    }

    private void setAir(int x, int y, int z) {
      air[(y * sizeZ + z) * sizeX + x] = true;
    }
  }

  /** A world rejecting blocks matching a configured material. */
  private static final class RejectingWorld implements ScannerWorld {
    private final String material;

    RejectingWorld(String material) {
      this.material = material;
    }

    @Override
    public String materialAt(int x, int y, int z) {
      return material;
    }

    @Override
    public boolean airAt(int x, int y, int z) {
      return false;
    }
  }

  /** A world with a capturable hull touching a forbidden water region. */
  private static final class HullAndWaterWorld implements ScannerWorld {
    private final Set<String> hull = new HashSet<>();

    HullAndWaterWorld() {
      hull.add("0,0,0");
      hull.add("1,0,0");
    }

    @Override
    public String materialAt(int x, int y, int z) {
      return hull.contains(key(x, y, z)) ? STONE : WATER;
    }

    @Override
    public boolean airAt(int x, int y, int z) {
      return false;
    }

    private static String key(int x, int y, int z) {
      return x + "," + y + "," + z;
    }
  }

  @Test
  void visitedKeysDistinguishOverlappingPackedCoordinates() {
    CoordKey origin = new CoordKey(0, 0, 0);
    CoordKey colliding = new CoordKey(1, 65536, 0);
    assertNotEquals(origin, colliding);
    assertEquals(origin, new CoordKey(0, 0, 0));
  }

  @Test
  void collectsWholeComponentFromSeed() {
    TestWorld world = new TestWorld(3, 2, 3, STONE);
    ScanResult result = ShipScanner.scan(world, new Seed(1, 1, 1), Integer.MAX_VALUE, Set.of());
    assertEquals(18, result.captured().size());
  }

  @Test
  void stopsAtConfiguredLimitWithoutMutating() {
    TestWorld world = new TestWorld(10, 10, 10, STONE);
    ScanResult result = ShipScanner.scan(world, new Seed(5, 5, 5), 10, Set.of());
    assertFalse(result.complete());
    assertNull(result.captured());
  }

  @Test
  void rejectsForbiddenMaterial() {
    RejectingWorld world = new RejectingWorld(WATER);
    ScanResult result =
        ShipScanner.scan(world, new Seed(0, 0, 0), Integer.MAX_VALUE, Set.of(WATER));
    assertFalse(result.complete());
    assertNull(result.captured());
  }

  @Test
  void doesNotCrossForbiddenWater() {
    HullAndWaterWorld world = new HullAndWaterWorld();
    ScanResult result =
        ShipScanner.scan(world, new Seed(0, 0, 0), Integer.MAX_VALUE, Set.of(WATER));
    assertTrue(result.complete());
    assertEquals(2, result.captured().size());
  }

  @Test
  void doesNotCrossAirGaps() {
    TestWorld world = new TestWorld(3, 3, 3, STONE);
    world.setAir(0, 1, 1);
    ScanResult result = ShipScanner.scan(world, new Seed(1, 1, 1), Integer.MAX_VALUE, Set.of());
    assertEquals(26, result.captured().size());
  }

  @Test
  void reportsComponentRootAtSeed() {
    TestWorld world = new TestWorld(3, 3, 3, STONE);
    ScanResult result = ShipScanner.scan(world, new Seed(1, 1, 1), Integer.MAX_VALUE, Set.of());
    assertEquals(1, result.rootX());
    assertEquals(1, result.rootY());
    assertEquals(1, result.rootZ());
  }

  @Test
  void capturesRelativePositionsOnly() {
    TestWorld world = new TestWorld(3, 3, 3, STONE);
    ScanResult result = ShipScanner.scan(world, new Seed(1, 1, 1), Integer.MAX_VALUE, Set.of());
    assertTrue(
        result.captured().stream().anyMatch(pos -> pos.x() == 0 && pos.y() == 0 && pos.z() == 0));
    assertTrue(
        result.captured().stream().anyMatch(pos -> pos.x() == 1 && pos.y() == 1 && pos.z() == 1));
  }
}
