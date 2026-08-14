package dev.jlo.ships.config;

import java.util.Set;
import java.util.UUID;

/** Immutable plugin settings loaded from {@code config.yml}. */
public final class ShipConfig {
  /** Maximum captured blocks per ship. */
  private final int maximumBlocks;

  /** Maximum command targeting distance in blocks. */
  private final int targetDistance;

  /** Material registry names that cannot be assembled. */
  private final Set<String> forbiddenMaterials;

  /** World identifiers where assembly is disabled. */
  private final Set<UUID> disabledWorlds;

  /**
   * Creates the configuration.
   *
   * @param maximumBlocks the maximum captured blocks per ship
   * @param targetDistance the maximum command targeting distance
   * @param forbiddenMaterials materials that cannot be assembled
   * @param disabledWorlds worlds where assembly is disabled
   */
  public ShipConfig(
      int maximumBlocks,
      int targetDistance,
      Set<String> forbiddenMaterials,
      Set<UUID> disabledWorlds) {
    this.maximumBlocks = maximumBlocks;
    this.targetDistance = targetDistance;
    this.forbiddenMaterials = Set.copyOf(forbiddenMaterials);
    this.disabledWorlds = Set.copyOf(disabledWorlds);
  }

  /**
   * @return the maximum captured blocks per ship
   */
  public int maximumBlocks() {
    return maximumBlocks;
  }

  /**
   * @return the maximum command targeting distance in blocks
   */
  public int targetDistance() {
    return targetDistance;
  }

  /**
   * @return material registry names that cannot be assembled
   */
  public Set<String> forbiddenMaterials() {
    return forbiddenMaterials;
  }

  /**
   * @return world identifiers where assembly is disabled
   */
  public Set<UUID> disabledWorlds() {
    return disabledWorlds;
  }

  /**
   * @param worldId the world identifier to check
   * @return true when assembly is permitted in the given world
   */
  public boolean worldEnabled(UUID worldId) {
    return !disabledWorlds.contains(worldId);
  }
}
