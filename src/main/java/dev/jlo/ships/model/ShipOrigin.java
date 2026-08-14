package dev.jlo.ships.model;

import java.util.UUID;

/**
 * Immutable identity and world placement for a ship. The origin is the
 * absolute integer position that relative block snapshots are measured
 * against.
 */
public final class ShipOrigin {
  private final UUID worldId;
  private final int x;
  private final int y;
  private final int z;

  /** Creates a ship origin. */
  public ShipOrigin(UUID worldId, int x, int y, int z) {
    this.worldId = worldId;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /** Returns the world identifier. */
  public UUID worldId() {
    return worldId;
  }

  /** Returns the origin x coordinate. */
  public int x() {
    return x;
  }

  /** Returns the origin y coordinate. */
  public int y() {
    return y;
  }

  /** Returns the origin z coordinate. */
  public int z() {
    return z;
  }
}