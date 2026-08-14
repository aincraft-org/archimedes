package dev.jlo.ships.model;

import java.util.Objects;

/** A single captured ship block: relative position plus exact block data. */
public final class ShipBlock {
  private final BlockPos pos;
  private final String blockData;

  /** Creates a ship block snapshot. */
  public ShipBlock(BlockPos pos, String blockData) {
    this.pos = Objects.requireNonNull(pos, "pos");
    this.blockData = Objects.requireNonNull(blockData, "blockData");
  }

  /** Returns the block position relative to the ship origin. */
  public BlockPos pos() {
    return pos;
  }

  /** Returns the exact serialized block data. */
  public String blockData() {
    return blockData;
  }
}