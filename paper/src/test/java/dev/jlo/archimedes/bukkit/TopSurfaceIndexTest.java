package dev.jlo.archimedes.bukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.archimedes.model.BlockPos;
import dev.jlo.archimedes.model.Ship;
import dev.jlo.archimedes.model.ShipBlock;
import dev.jlo.archimedes.model.ShipOrigin;
import java.util.List;
import java.util.UUID;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/** Tests the top-surface grid used for ship entity carry. */
class TopSurfaceIndexTest {
  private static final UUID WORLD = UUID.randomUUID();
  private static final UUID OWNER = UUID.randomUUID();
  private static final String STONE = "minecraft:stone";

  @Test
  void overlapForEntityStandingOnTopBlock() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));

    TopSurfaceIndex index = TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0)), ship);

    // Feet of a player standing on the top surface at y=201, body 1.8 blocks tall.
    BoundingBox player = new BoundingBox(100.25, 201.0, 300.25, 100.75, 202.8, 300.75);
    assertTrue(index.overlaps(player, 0.0));
  }

  @Test
  void noOverlapWhenEntityIsTooHigh() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));

    TopSurfaceIndex index = TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0)), ship);

    // Entity floating well above the small upper contact margin.
    BoundingBox floating = new BoundingBox(100.25, 204.0, 300.25, 100.75, 205.0, 300.75);
    assertFalse(index.overlaps(floating, 0.0));
  }

  @Test
  void noOverlapWhenEntityIsAirborne() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));

    TopSurfaceIndex index = TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0)), ship);

    // Player mid-jump, feet one block above the top surface.
    BoundingBox jumping = new BoundingBox(100.25, 202.0, 300.25, 100.75, 203.8, 300.75);
    assertFalse(index.overlaps(jumping, 0.0));
  }

  @Test
  void overlapWhenEntityIsSlightlyAboveTheTopBlock() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));

    TopSurfaceIndex index = TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0)), ship);

    // Player bobbing or stepping within the upper contact margin while walking on the ship.
    BoundingBox walkingBob = new BoundingBox(100.25, 201.2, 300.25, 100.75, 203.0, 300.75);
    assertTrue(index.overlaps(walkingBob, 0.0));
  }

  @Test
  void noOverlapWhenEntityIsJumpingOffTheTopBlock() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));

    TopSurfaceIndex index = TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0)), ship);

    // Player at the start of a jump; their feet are above the upper contact margin.
    BoundingBox jumping = new BoundingBox(100.25, 201.4, 300.25, 100.75, 203.2, 300.75);
    assertFalse(index.overlaps(jumping, 0.0));
  }

  @Test
  void noOverlapWhenEntityIsBesideTheBlock() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));

    TopSurfaceIndex index = TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0)), ship);

    // Entity one block to the east of the top surface.
    BoundingBox beside = new BoundingBox(101.1, 201.0, 300.25, 101.5, 202.8, 300.75);
    assertFalse(index.overlaps(beside, 0.0));
  }

  @Test
  void overlapForEntityStraddlingTwoTopBlocks() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(
                new ShipBlock(new BlockPos(0, 0, 0), STONE),
                new ShipBlock(new BlockPos(1, 0, 0), STONE)));

    TopSurfaceIndex index =
        TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)), ship);

    // Player standing on the boundary between the two top blocks.
    BoundingBox player = new BoundingBox(100.75, 201.0, 300.25, 101.25, 202.8, 300.75);
    assertTrue(index.overlaps(player, 0.0));
  }

  @Test
  void overlapForNegativeWorldOrigin() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, -100, 200, -100),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));

    TopSurfaceIndex index = TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0)), ship);

    BoundingBox player = new BoundingBox(-99.75, 201.0, -99.75, -99.25, 202.8, -99.25);
    assertTrue(index.overlaps(player, 0.0));
  }

  @Test
  void overlapShiftsWithPoseY() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 100, 200, 300),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));

    TopSurfaceIndex index = TopSurfaceIndex.build(List.of(new BlockPos(0, 0, 0)), ship);

    // Player at the same location after the ship has moved up by 5 blocks.
    BoundingBox player = new BoundingBox(100.25, 206.0, 300.25, 100.75, 207.8, 300.75);
    assertTrue(index.overlaps(player, 5.0));
    assertFalse(index.overlaps(player, 0.0));
  }
}
