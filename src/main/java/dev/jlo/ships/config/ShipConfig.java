package dev.jlo.ships.config;

import java.util.Set;
import java.util.UUID;

/** Immutable plugin settings loaded from {@code config.yml}. */
public final class ShipConfig {
  private final int maximumBlocks;
  private final int targetDistance;
  private final Set<String> forbiddenMaterials;
  private final Set<UUID> disabledWorlds;

  /** Creates the configuration. */
  public ShipConfig(
      int maximumBlocks, int targetDistance, Set<String> forbiddenMaterials, Set<UUID> disabledWorlds) {
    this.maximumBlocks = maximumBlocks;
    this.targetDistance = targetDistance;
    this.forbiddenMaterials = Set.copyOf(forbiddenMaterials);
    this.disabledWorlds = Set.copyOf(disabledWorlds);
  }

  /** Returns the maximum captured blocks per ship. */
  public int maximumBlocks() {
    return maximumBlocks;
  }

  /** Returns the maximum command targeting distance in blocks. */
  public int targetDistance() {
    return targetDistance;
  }

  /** Returns material registry names that cannot be assembled. */
  public Set<String> forbiddenMaterials() {
    return forbiddenMaterials;
  }

  /** Returns world identifiers where assembly is disabled. */
  public Set<UUID> disabledWorlds() {
    return disabledWorlds;
  }

  /** Returns true when assembly is permitted in the given world. */
  public boolean worldEnabled(UUID worldId) {
    return !disabledWorlds.contains(worldId);
  }
}