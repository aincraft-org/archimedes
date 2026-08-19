package dev.mintychochip.archimedes.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for nearby-hull command targeting. */
class ShipTargetingTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000099");
  private static final String STONE = "minecraft:stone";

  @Test
  void prefersTheHullThePlayerIsStandingOn() {
    Vehicle distant = hull(WORLD, 1, 64, 1);
    Vehicle underfoot = hull(WORLD, 10, 64, 20);
    Vehicle found = ShipTargeting.nearest(List.of(distant, underfoot), WORLD, 10.5, 65.2, 20.5, 8);
    assertEquals(underfoot.id(), found.id());
  }

  @Test
  void ignoresAFirstOwnedShipThatIsFarAway() {
    Vehicle firstOwned = hull(WORLD, 0, 64, 790);
    Vehicle nearby = hull(WORLD, 10, 64, 938);
    Vehicle found = ShipTargeting.nearest(List.of(firstOwned, nearby), WORLD, 10.5, 65.0, 938.5, 8);
    assertEquals(nearby.id(), found.id());
  }

  @Test
  void returnsNullWhenEveryHullIsOutOfRange() {
    Vehicle far = hull(WORLD, 0, 64, 0);
    assertNull(ShipTargeting.nearest(List.of(far), WORLD, 100.0, 64.0, 100.0, 8));
  }

  @Test
  void ignoresShipsInAnotherWorld() {
    Vehicle otherWorld = hull(OTHER, 10, 64, 20);
    assertNull(ShipTargeting.nearest(List.of(otherWorld), WORLD, 10.5, 65.0, 20.5, 8));
  }

  @Test
  void keepsAJumpedPlayerOnTheDeckVolume() {
    Vehicle deck = hull(WORLD, 10, 64, 20);
    Vehicle found = ShipTargeting.nearest(List.of(deck), WORLD, 10.5, 65.4, 20.5, 8);
    assertEquals(deck.id(), found.id());
  }

  @Test
  void followsAFloatedPose() {
    Vehicle floated =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(WORLD, 10, 64, 20),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)),
            new ShipPose(0.0, 3.0, 0.0),
            true);
    Vehicle found = ShipTargeting.nearest(List.of(floated), WORLD, 10.5, 68.2, 20.5, 8);
    assertEquals(floated.id(), found.id());
  }

  private static Vehicle hull(UUID worldId, int x, int y, int z) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(worldId, x, y, z),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
  }
}
