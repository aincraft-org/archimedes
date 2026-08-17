package dev.mintychochip.archimedes.config;

import java.util.Map;
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

  /** Configured per-material densities. */
  private final Map<String, Double> materialDensities;

  /** Density used when a material has no explicit entry. */
  private final double defaultMaterialDensity;

  /** Mass of each rider. */
  private final double playerMass;

  /** Maximum downward displacement from the build site. */
  private final double maxFall;

  /** Mass tolerance used by equilibrium solving. */
  private final double massTolerance;

  /** Draft tolerance used to suppress tiny pose changes. */
  private final double draftTolerance;

  /**
   * Creates configuration with legacy physics defaults.
   *
   * <p>This delegates to the full constructor with an empty material-density map, default material
   * density {@code 1.0}, player mass {@code 80.0}, maximum fall {@code 16.0}, mass tolerance {@code
   * 1e-6}, and draft tolerance {@code 1e-3}. Set and map arguments are copied in their iteration
   * order where applicable and reject null arguments and null elements or entries with {@link
   * NullPointerException}.
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
    this(
        maximumBlocks,
        targetDistance,
        forbiddenMaterials,
        disabledWorlds,
        buoyancyEnabled,
        physicsTicks,
        bobAmplitude,
        maxRise,
        gravity,
        waterDensity,
        blockDensity,
        damping,
        Map.of(),
        1.0,
        80.0,
        16.0,
        1e-6,
        1e-3);
  }

  /**
   * Creates the configuration with explicit physics settings.
   *
   * <p>The supplied sets and map are defensively copied and exposed as unmodifiable collections.
   * Null collection/map arguments and null elements, keys, or values are rejected with {@link
   * NullPointerException}; scalar arguments are stored as supplied.
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
   * @param materialDensities per-material densities
   * @param defaultMaterialDensity default material density
   * @param playerMass mass of a rider
   * @param maxFall maximum fall distance
   * @param massTolerance mass equilibrium tolerance
   * @param draftTolerance waterline tolerance
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
      double damping,
      Map<String, Double> materialDensities,
      double defaultMaterialDensity,
      double playerMass,
      double maxFall,
      double massTolerance,
      double draftTolerance) {
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
    this.materialDensities = Map.copyOf(materialDensities);
    this.defaultMaterialDensity = defaultMaterialDensity;
    this.playerMass = playerMass;
    this.maxFall = maxFall;
    this.massTolerance = massTolerance;
    this.draftTolerance = draftTolerance;
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
   * @return an unmodifiable set of material registry names that cannot be assembled
   */
  public Set<String> forbiddenMaterials() {
    return forbiddenMaterials;
  }

  /**
   * @return an unmodifiable set of world identifiers where assembly is disabled
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

  /**
   * Returns configured per-material densities.
   *
   * @return an unmodifiable map keyed by material registry name, in the copied map's iteration
   *     order
   */
  public Map<String, Double> materialDensities() {
    return materialDensities;
  }

  /**
   * Returns the density used when a material has no explicit entry.
   *
   * @return fallback material density
   */
  public double defaultMaterialDensity() {
    return defaultMaterialDensity;
  }

  /**
   * Returns the mass assigned to each rider.
   *
   * @return rider mass
   */
  public double playerMass() {
    return playerMass;
  }

  /**
   * Returns the maximum downward displacement from the build site.
   *
   * @return maximum fall distance
   */
  public double maxFall() {
    return maxFall;
  }

  /**
   * Returns the tolerance used when comparing equilibrium masses.
   *
   * @return mass comparison tolerance
   */
  public double massTolerance() {
    return massTolerance;
  }

  /**
   * Returns the tolerance below which draft changes are ignored.
   *
   * @return draft movement tolerance
   */
  public double draftTolerance() {
    return draftTolerance;
  }
}
