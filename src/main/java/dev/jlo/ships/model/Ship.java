package dev.jlo.ships.model;

import java.util.List;
import java.util.UUID;

/** Immutable assembled ship with its owner, origin, and captured blocks. */
public final class Ship {
  private final UUID id;
  private final UUID ownerId;
  private final ShipOrigin origin;
  private final List<ShipBlock> blocks;

  /** Creates a ship. */
  public Ship(UUID id, UUID ownerId, ShipOrigin origin, List<ShipBlock> blocks) {
    this.id = id;
    this.ownerId = ownerId;
    this.origin = origin;
    this.blocks = List.copyOf(blocks);
  }

  /** Returns the ship identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the owning player identifier. */
  public UUID ownerId() {
    return ownerId;
  }

  /** Returns the absolute world origin. */
  public ShipOrigin origin() {
    return origin;
  }

  /** Returns the captured blocks in deterministic order. */
  public List<ShipBlock> blocks() {
    return blocks;
  }

  /** Returns the number of captured blocks. */
  public int blockCount() {
    return blocks.size();
  }
}