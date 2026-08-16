package dev.jlo.ships.model;

import java.util.List;
import java.util.UUID;

/** Immutable assembled ship with its owner, origin, and captured blocks. */
public final class Ship {
  /** Ship identifier. */
  private final UUID id;

  /** Owning player identifier. */
  private final UUID ownerId;

  /** Absolute world origin. */
  private final ShipOrigin origin;

  /** Captured blocks in deterministic order. */
  private final List<ShipBlock> blocks;

  /** Runtime vertical pose. */
  private ShipPose pose;

  /** Whether buoyancy is active for this ship. */
  private boolean buoyancyEnabled;

  /**
   * Creates a ship.
   *
   * @param id the ship identifier
   * @param ownerId the owning player identifier
   * @param origin the absolute world origin
   * @param blocks the captured blocks
   */
  public Ship(UUID id, UUID ownerId, ShipOrigin origin, List<ShipBlock> blocks) {
    this(id, ownerId, origin, blocks, new ShipPose(0), true);
  }

  /**
   * Creates a ship with an explicit pose and buoyancy flag.
   *
   * @param id the ship identifier
   * @param ownerId the owning player identifier
   * @param origin the absolute world origin
   * @param blocks the captured blocks
   * @param pose the runtime vertical pose
   * @param buoyancyEnabled whether buoyancy is active
   */
  public Ship(
      UUID id,
      UUID ownerId,
      ShipOrigin origin,
      List<ShipBlock> blocks,
      ShipPose pose,
      boolean buoyancyEnabled) {
    this.id = id;
    this.ownerId = ownerId;
    this.origin = origin;
    this.blocks = List.copyOf(blocks);
    this.pose = pose;
    this.buoyancyEnabled = buoyancyEnabled;
  }

  /**
   * @return the ship identifier
   */
  public UUID id() {
    return id;
  }

  /**
   * @return the owning player identifier
   */
  public UUID ownerId() {
    return ownerId;
  }

  /**
   * @return the absolute world origin
   */
  public ShipOrigin origin() {
    return origin;
  }

  /**
   * @return the captured blocks in deterministic order
   */
  public List<ShipBlock> blocks() {
    return blocks;
  }

  /**
   * @return the number of captured blocks
   */
  public int blockCount() {
    return blocks.size();
  }

  /**
   * @return the runtime vertical pose
   */
  public ShipPose pose() {
    return pose;
  }

  /**
   * Sets the runtime vertical pose.
   *
   * @param newPose the new pose
   */
  public void setPose(ShipPose newPose) {
    this.pose = newPose;
  }

  /**
   * @return whether buoyancy is active
   */
  public boolean buoyancyEnabled() {
    return buoyancyEnabled;
  }

  /**
   * Sets whether buoyancy is active.
   *
   * @param enabled the new flag
   */
  public void setBuoyancyEnabled(boolean enabled) {
    this.buoyancyEnabled = enabled;
  }
}
