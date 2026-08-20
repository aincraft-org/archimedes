package dev.mintychochip.archimedes.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for model-free captured cannon discovery. */
class ShipCannonsTest {
  @Test
  void discoversEveryHorizontalControlAttachment() {
    assertControl(CannonDirection.EAST, 1, 0, 0, "east");
    assertControl(CannonDirection.WEST, -1, 0, 0, "west");
    assertControl(CannonDirection.NORTH, 0, 0, -1, "north");
    assertControl(CannonDirection.SOUTH, 0, 0, 1, "south");
  }

  @Test
  void preservesVerticalFacingAndFloorControl() {
    Vehicle ship =
        vehicle(
            block(0, 0, 0, "minecraft:dispenser[facing=up,triggered=false]"),
            block(0, 1, 0, "minecraft:stone_button[face=floor,facing=north,powered=false]"));

    assertEquals(CannonDirection.UP, ShipCannons.discover(ship).getFirst().direction());
  }

  @Test
  void rejectsDetachedAndAmbiguousControls() {
    Vehicle detached =
        vehicle(
            block(0, 0, 0, "minecraft:dispenser[facing=south,triggered=false]"),
            block(1, 0, 0, "minecraft:stone_button[face=wall,facing=west,powered=false]"));
    Vehicle ambiguous =
        vehicle(
            block(0, 0, 0, "minecraft:dispenser[facing=south,triggered=false]"),
            block(1, 0, 0, "minecraft:stone_button[face=wall,facing=east,powered=false]"),
            block(-1, 0, 0, "minecraft:stone_button[face=wall,facing=west,powered=false]"));

    assertTrue(ShipCannons.discover(detached).isEmpty());
    assertTrue(ShipCannons.discover(ambiguous).isEmpty());
  }

  private static void assertControl(
      CannonDirection cannonDirection, int x, int y, int z, String buttonFacing) {
    Vehicle ship =
        vehicle(
            block(
                0,
                0,
                0,
                "minecraft:dispenser[facing="
                    + cannonDirection.name().toLowerCase(java.util.Locale.ROOT)
                    + ",triggered=false]"),
            block(
                x,
                y,
                z,
                "minecraft:stone_button[face=wall,facing=" + buttonFacing + ",powered=false]"));

    assertEquals(
        List.of(new CannonMount(new BlockPos(0, 0, 0), new BlockPos(x, y, z), cannonDirection)),
        ShipCannons.discover(ship));
    assertTrue(ShipCannons.atControl(ship, new BlockPos(x, y, z)).isPresent());
  }

  private static Vehicle vehicle(ShipBlock... blocks) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
        List.of(blocks));
  }

  private static ShipBlock block(int x, int y, int z, String data) {
    return new ShipBlock(new BlockPos(x, y, z), data);
  }
}
