package dev.mintychochip.archimedes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests for canonical ship coordinate projections. */
class ShipTransformTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void projectsVisualAndAuthoritativeCoordinates() {
    Ship ship = ship(new ShipOrigin(WORLD, 100, 200, 300), 1.75);
    BlockPos relative = new BlockPos(2, -1, 3);

    ShipTransform.VisualPosition visual = ShipTransform.visual(ship, relative);
    BlockPos cell = ShipTransform.cell(ship, relative);
    assertEquals(102.0, visual.x());
    assertEquals(200.75, visual.y());
    assertEquals(303.0, visual.z());
    assertEquals(102, cell.x());
    assertEquals(200, cell.y());
    assertEquals(303, cell.z());
  }

  @Test
  void projectsFractionalCollisionAnchorFromVisualPosition() {
    Ship ship = ship(new ShipOrigin(WORLD, 100, 200, 300), 0.25);

    ShipTransform.CollisionAnchor anchor =
        ShipTransform.collisionAnchor(ship, new BlockPos(2, -1, 3));

    assertEquals(102.5, anchor.x());
    assertEquals(199.25, anchor.y());
    assertEquals(303.5, anchor.z());
  }

  @Test
  void floorsNegativePoseForAuthoritativeCell() {
    Ship ship = ship(new ShipOrigin(WORLD, 100, 200, 300), -0.25);
    BlockPos cell = ShipTransform.cell(ship, new BlockPos(2, -1, 3));
    assertEquals(102, cell.x());
    assertEquals(198, cell.y());
    assertEquals(303, cell.z());
  }

  private static Ship ship(ShipOrigin origin, double pose) {
    return new Ship(
        UUID.randomUUID(),
        UUID.randomUUID(),
        origin,
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:stone")),
        new ShipPose(pose),
        true);
  }
}
