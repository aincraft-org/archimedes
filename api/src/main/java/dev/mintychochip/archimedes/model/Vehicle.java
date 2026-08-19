package dev.mintychochip.archimedes.model;

import java.util.List;
import java.util.UUID;

/** Assembled vehicle whose captured block list is immutable while runtime state remains mutable. */
public final class Vehicle {
  /** Vehicle identifier. */
  private final UUID id;

  /** Owning player identifier. */
  private final UUID ownerId;

  /** Absolute world origin. */
  private final ShipOrigin origin;

  /** Captured blocks copied into an unmodifiable list in the supplied iteration order. */
  private final List<ShipBlock> blocks;

  /** Runtime vertical pose. */
  private ShipPose pose;

  /** Whether buoyancy is active for this vehicle. */
  private boolean buoyancyEnabled;

  /** Whether sail forces are active for this vehicle. */
  private boolean sailsEnabled;

  /** Whether engine forces are active for this vehicle. */
  private boolean enginesEnabled;

  /**
   * Creates a vehicle with its initial pose at zero and buoyancy, sails, and engines enabled.
   *
   * <p>The captured block list is copied in iteration order and rejects null elements. A null
   * {@code blocks} argument causes a {@link NullPointerException}.
   *
   * @param id the vehicle identifier; may be {@code null}
   * @param ownerId the owning player identifier; may be {@code null}
   * @param origin the absolute world origin; may be {@code null}
   * @param blocks the captured blocks
   */
  public Vehicle(UUID id, UUID ownerId, ShipOrigin origin, List<ShipBlock> blocks) {
    this(id, ownerId, origin, blocks, new ShipPose(0), true);
  }

  /**
   * Creates a vehicle with the supplied runtime state and sails and engines enabled.
   *
   * <p>The captured block list is copied in iteration order and rejects null elements. A null
   * {@code blocks} argument causes a {@link NullPointerException}; the other arguments are stored
   * as supplied.
   *
   * @param id the vehicle identifier; may be {@code null}
   * @param ownerId the owning player identifier; may be {@code null}
   * @param origin the absolute world origin; may be {@code null}
   * @param blocks the captured blocks
   * @param pose the runtime vertical pose; may be {@code null}
   * @param buoyancyEnabled whether buoyancy is active
   */
  public Vehicle(
      UUID id,
      UUID ownerId,
      ShipOrigin origin,
      List<ShipBlock> blocks,
      ShipPose pose,
      boolean buoyancyEnabled) {
    this(id, ownerId, origin, blocks, pose, buoyancyEnabled, true, true);
  }

  /**
   * Creates a vehicle with the supplied runtime state and actuator flags.
   *
   * <p>The captured block list is copied in iteration order and rejects null elements. A null
   * {@code blocks} argument causes a {@link NullPointerException}; the other arguments are stored
   * as supplied.
   *
   * @param id the vehicle identifier; may be {@code null}
   * @param ownerId the owning player identifier; may be {@code null}
   * @param origin the absolute world origin; may be {@code null}
   * @param blocks the captured blocks
   * @param pose the runtime vertical pose; may be {@code null}
   * @param buoyancyEnabled whether buoyancy is active
   * @param sailsEnabled whether sail forces are active
   * @param enginesEnabled whether engine forces are active
   */
  public Vehicle(
      UUID id,
      UUID ownerId,
      ShipOrigin origin,
      List<ShipBlock> blocks,
      ShipPose pose,
      boolean buoyancyEnabled,
      boolean sailsEnabled,
      boolean enginesEnabled) {
    this.id = id;
    this.ownerId = ownerId;
    this.origin = origin;
    this.blocks = List.copyOf(blocks);
    this.pose = pose;
    this.buoyancyEnabled = buoyancyEnabled;
    this.sailsEnabled = sailsEnabled;
    this.enginesEnabled = enginesEnabled;
  }

  /**
   * @return the vehicle identifier
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
   * @return the captured blocks in supplied iteration order as an unmodifiable list
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
   * Sets the runtime vertical pose. The supplied reference is stored as-is.
   *
   * @param newPose the new pose; may be {@code null}
   */
  public void setPose(ShipPose newPose) {
    this.pose = newPose;
  }

  /**
   * Returns the runtime vertical pose.
   *
   * @return the current runtime pose
   */
  public ShipPose pose() {
    return pose;
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

  /**
   * @return whether sail forces are active
   */
  public boolean sailsEnabled() {
    return sailsEnabled;
  }

  /**
   * Sets whether sail forces are active.
   *
   * @param enabled the new flag
   */
  public void setSailsEnabled(boolean enabled) {
    this.sailsEnabled = enabled;
  }

  /**
   * @return whether engine forces are active
   */
  public boolean enginesEnabled() {
    return enginesEnabled;
  }

  /**
   * Sets whether engine forces are active.
   *
   * @param enabled the new flag
   */
  public void setEnginesEnabled(boolean enabled) {
    this.enginesEnabled = enabled;
  }
}
