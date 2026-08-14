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

  /** Whether buoyancy is enabled globally. */
  private final boolean buoyancyEnabled;

  /** Server ticks between physics integrations. */
  private final int physicsTicks;

  /** Maximum vertical bob amplitude around equilibrium. */
  private final double bobAmplitude;

  /** Maximum vertical rise from the build site. */
  private final double maxRise;

  /** Gravity constant. */
  private final double gravity;

  /** Water density constant. */
  private final double waterDensity;

  /** Block density constant. */
  private final double blockDensity;

  /** Velocity damping factor. */
  private final double damping;

  /**
   * Creates the configuration.
   *
   * @param maximumBlocks the maximum captured blocks per ship
   * @param targetDistance the maximum command targeting distance
   * @param forbiddenMaterials materials that cannot be assembled
   * @param disabledWorlds worlds where assembly is disabled
   * @param buoyancyEnabled whether buoyancy is enabled globally
   * @param physicsTicks physics tick interval
   * @param bobAmplitude max bob amplitude
   * @param maxRise max rise from build site
   * @param gravity gravity constant
   * @param waterDensity water density constant
   * @param blockDensity block density constant
   * @param damping velocity damping factor
   */
  public ShipConfig(
      int maximumBlocks,
      int targetDistance,
      Set<String> forbiddenMaterials,
      Set<UUID> disabledWorlds,
      boolean buoyancyEnabled,
      int physicsTicks,
      double bobAmplitude,
      double maxRise,
      double gravity,
      double waterDensity,
      double blockDensity,
      double damping) {
    this.maximumBlocks = maximumBlocks;
    this.targetDistance = targetDistance;
    this.forbiddenMaterials = Set.copyOf(forbiddenMaterials);
    this.disabledWorlds = Set.copyOf(disabledWorlds);
    this.buoyancyEnabled = buoyancyEnabled;
    this.physicsTicks = physicsTicks;
    this.bobAmplitude = bobAmplitude;
    this.maxRise = maxRise;
    this.gravity = gravity;
    this.waterDensity = waterDensity;
    this.blockDensity = blockDensity;
    this.damping = damping;
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

  /**
   * @return whether buoyancy is enabled globally
   */
  public boolean buoyancyEnabled() {
    return buoyancyEnabled;
  }

  /**
   * @return the physics tick interval
   */
  public int physicsTicks() {
    return physicsTicks;
  }

  /**
   * @return the max vertical bob amplitude
   */
  public double bobAmplitude() {
    return bobAmplitude;
  }

  /**
   * @return the maximum rise from the build site
   */
  public double maxRise() {
    return maxRise;
  }

  /**
   * @return the gravity constant
   */
  public double gravity() {
    return gravity;
  }

  /**
   * @return the water density constant
   */
  public double waterDensity() {
    return waterDensity;
  }

  /**
   * @return the block density constant
   */
  public double blockDensity() {
    return blockDensity;
  }

  /**
   * @return the velocity damping factor
   */
  public double damping() {
    return damping;
  }
}
