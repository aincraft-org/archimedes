package dev.mintychochip.archimedes.model;

import java.util.Objects;

/** A single captured ship block: relative position plus exact block data. */
public final class ShipBlock {
  /** Relative block position. */
  private final BlockPos pos;

  /** Serialized exact block data. */
  private final String blockData;

  /**
   * Creates a ship block snapshot.
   *
   * @param pos the relative block position
   * @param blockData the serialized exact block data
   */
  public ShipBlock(BlockPos pos, String blockData) {
    this.pos = Objects.requireNonNull(pos, "pos");
    this.blockData = Objects.requireNonNull(blockData, "blockData");
  }

  /**
   * @return the block position relative to the ship origin
   */
  public BlockPos pos() {
    return pos;
  }

  /**
   * @return the exact serialized block data
   */
  public String blockData() {
    return blockData;
  }
}
