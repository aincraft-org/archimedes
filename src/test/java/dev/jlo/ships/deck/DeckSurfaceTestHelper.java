package dev.jlo.ships.deck;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipOrigin;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Shared helpers for deck tests. */
public final class DeckSurfaceTestHelper {
  private DeckSurfaceTestHelper() {}

  /** Builds a ship at origin (100,200,300) from relative positions. */
  public static Ship shipWith(BlockPos... positions) {
    ShipOrigin origin =
        new ShipOrigin(UUID.fromString("00000000-0000-0000-0000-000000000001"), 100, 200, 300);
    List<ShipBlock> blocks =
        Arrays.stream(positions).map(pos -> new ShipBlock(pos, "minecraft:stone")).toList();
    return new Ship(UUID.randomUUID(), UUID.randomUUID(), origin, blocks);
  }
}
