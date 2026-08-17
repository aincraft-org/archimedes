package dev.mintychochip.archimedes.model;

import java.util.UUID;

/**
 * Immutable identity and world placement for a ship. The origin is the absolute integer position
 * that relative block snapshots are measured against.
 */
public final class ShipOrigin {
  /** World identifier the ship belongs to. */
  private final UUID worldId;

  /** Origin x coordinate. */
  private final int x;

  /** Origin y coordinate. */
  private final int y;

  /** Origin z coordinate. */
  private final int z;

  /**
   * Creates a ship origin.
   *
   * @param worldId the world identifier
   * @param x the origin x coordinate
   * @param y the origin y coordinate
   * @param z the origin z coordinate
   */
  public ShipOrigin(UUID worldId, int x, int y, int z) {
    this.worldId = worldId;
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * @return the world identifier
   */
  public UUID worldId() {
    return worldId;
  }

  /**
   * @return the origin x coordinate
   */
  public int x() {
    return x;
  }

  /**
   * @return the origin y coordinate
   */
  public int y() {
    return y;
  }

  /**
   * @return the origin z coordinate
   */
  public int z() {
    return z;
  }
}
