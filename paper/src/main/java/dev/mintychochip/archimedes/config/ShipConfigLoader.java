package dev.mintychochip.archimedes.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Builds a validated {@link ShipConfig} from Bukkit configuration values. */
public final class ShipConfigLoader {
  /** Key for the maximum assembled blocks setting. */
  public static final String MAXIMUM_BLOCKS_KEY = "maximum-blocks";

  /** Key for the command targeting distance setting. */
  public static final String TARGET_DISTANCE_KEY = "target-distance";

  /** Key for the forbidden material list setting. */
  public static final String FORBIDDEN_MATERIALS_KEY = "forbidden-materials";

  /** Key for the engine material list setting. */
  public static final String ENGINE_MATERIALS_KEY = "engine-materials";

  /** Key for the envelope material list setting. */
  public static final String ENVELOPE_MATERIALS_KEY = "envelope-materials";

  /** Key for the engine thrust coefficient. */
  public static final String ENGINE_THRUST_KEY = "engine-thrust";

  /** Default engine materials when the list is omitted. */
  private static final Set<String> DEFAULT_ENGINE_MATERIALS =
      Set.of("minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker");

  /** Default envelope materials when the list is omitted. */
  private static final Set<String> DEFAULT_ENVELOPE_MATERIALS =
      Set.of("minecraft:slime_block", "minecraft:honey_block");

  /** Key for the disabled world identifier list setting. */
  public static final String DISABLED_WORLDS_KEY = "disabled-worlds";

  /** Key for the buoyancy master switch. */
  public static final String BUOYANCY_ENABLED_KEY = "buoyancy-enabled";

  /** Key for the physics tick interval. */
  public static final String PHYSICS_TICKS_KEY = "physics-ticks";

  /** Key for the max vertical bob amplitude. */
  public static final String BOB_AMPLITUDE_KEY = "bob-amplitude";

  /** Key for the maximum rise from build site. */
  public static final String MAX_RISE_KEY = "max-rise";

  /** Key for the gravity constant. */
  public static final String GRAVITY_KEY = "gravity";

  /** Key for the water density constant. */
  public static final String WATER_DENSITY_KEY = "water-density";

  /** Key for the block density constant. */
  public static final String BLOCK_DENSITY_KEY = "block-density";

  /** Key for the velocity damping factor. */
  public static final String DAMPING_KEY = "damping";

  private ShipConfigLoader() {}

  /**
   * Builds a configuration, throwing {@link IllegalArgumentException} when a value is missing or
   * unsafe.
   *
   * @param configuration the Bukkit file configuration
   * @return the validated ship configuration
   */
  public static ShipConfig load(FileConfiguration configuration) {
    int maximumBlocks = configuration.getInt(MAXIMUM_BLOCKS_KEY, 0);
    if (maximumBlocks < 1) {
      throw new IllegalArgumentException("maximum-blocks must be a positive integer");
    }
    int targetDistance = configuration.getInt(TARGET_DISTANCE_KEY, 0);
    if (targetDistance < 1) {
      throw new IllegalArgumentException("target-distance must be a positive integer");
    }
    Set<String> forbidden = new HashSet<>();
    for (String value : configuration.getStringList(FORBIDDEN_MATERIALS_KEY)) {
      if (!value.isBlank()) {
        forbidden.add(value.toLowerCase(Locale.ROOT));
      }
    }
    Set<String> engineMaterials =
        loadMaterialKeys(configuration, ENGINE_MATERIALS_KEY, DEFAULT_ENGINE_MATERIALS);
    Set<String> envelopeMaterials =
        loadMaterialKeys(configuration, ENVELOPE_MATERIALS_KEY, DEFAULT_ENVELOPE_MATERIALS);
    for (String key : envelopeMaterials) {
      if (isSailCloth(key)) {
        throw new IllegalArgumentException("envelope-materials cannot include sail cloth");
      }
    }
    double engineThrust = configuration.getDouble(ENGINE_THRUST_KEY, 1.0);
    if (!Double.isFinite(engineThrust) || engineThrust < 0) {
      throw new IllegalArgumentException("engine-thrust must be a finite non-negative number");
    }
    Set<UUID> disabledWorlds = new HashSet<>();
    for (String value : configuration.getStringList(DISABLED_WORLDS_KEY)) {
      try {
        disabledWorlds.add(UUID.fromString(value));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("invalid world id in disabled-worlds: " + value);
      }
    }
    boolean buoyancyEnabled = configuration.getBoolean(BUOYANCY_ENABLED_KEY, true);
    int physicsTicks = configuration.getInt(PHYSICS_TICKS_KEY, 1);
    if (physicsTicks < 1) {
      throw new IllegalArgumentException("physics-ticks must be a positive integer");
    }
    double bobAmplitude = configuration.getDouble(BOB_AMPLITUDE_KEY, 0.5);
    if (!Double.isFinite(bobAmplitude) || bobAmplitude < 0) {
      throw new IllegalArgumentException("bob-amplitude must be a finite non-negative number");
    }
    double maxRise = configuration.getDouble(MAX_RISE_KEY, 16.0);
    if (!Double.isFinite(maxRise) || maxRise < 0) {
      throw new IllegalArgumentException("max-rise must be a finite non-negative number");
    }
    double gravity = configuration.getDouble(GRAVITY_KEY, 10.0);
    if (!Double.isFinite(gravity) || gravity <= 0) {
      throw new IllegalArgumentException("gravity must be a finite positive number");
    }
    double waterDensity = configuration.getDouble(WATER_DENSITY_KEY, 1.0);
    if (!Double.isFinite(waterDensity) || waterDensity <= 0) {
      throw new IllegalArgumentException("water-density must be a finite positive number");
    }
    double blockDensity = configuration.getDouble(BLOCK_DENSITY_KEY, 0.5);
    if (!Double.isFinite(blockDensity) || blockDensity <= 0) {
      throw new IllegalArgumentException("block-density must be a finite positive number");
    }
    double damping = configuration.getDouble(DAMPING_KEY, 0.9);
    if (!Double.isFinite(damping) || damping < 0 || damping > 1) {
      throw new IllegalArgumentException("damping must be a finite number between 0 and 1");
    }
    ConfigurationSection buoyancy = configuration.getConfigurationSection("buoyancy");
    if (buoyancy == null) {
      buoyancy = configuration.createSection("buoyancy");
    }
    Map<String, Double> materialDensities = new HashMap<>();
    ConfigurationSection densities = buoyancy.getConfigurationSection("material-densities");
    if (densities != null) {
      for (String key : densities.getKeys(false)) {
        double value = densities.getDouble(key);
        if (!Double.isFinite(value) || value <= 0) {
          throw new IllegalArgumentException("bad density: " + key);
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!isNamespacedMaterialKey(normalized)) {
          throw new IllegalArgumentException("invalid material key: " + key);
        }
        if (materialDensities.containsKey(normalized)) {
          throw new IllegalArgumentException("duplicate material density: " + normalized);
        }
        materialDensities.put(normalized, value);
      }
    }
    double defaultMaterialDensity =
        positiveFinite(
            buoyancy.getDouble("default-material-density", 1.0), "default-material-density");
    double playerMass = positiveFinite(buoyancy.getDouble("player-mass", 80.0), "player-mass");
    double maxFall = positiveFinite(buoyancy.getDouble("max-fall", 16.0), "max-fall");
    double massTolerance =
        positiveFinite(buoyancy.getDouble("mass-tolerance", 1e-6), "mass-tolerance");
    double draftTolerance =
        positiveFinite(buoyancy.getDouble("draft-tolerance", 1e-3), "draft-tolerance");
    return new ShipConfig(
        maximumBlocks,
        targetDistance,
        forbidden,
        disabledWorlds,
        buoyancyEnabled,
        physicsTicks,
        bobAmplitude,
        maxRise,
        gravity,
        waterDensity,
        blockDensity,
        damping,
        materialDensities,
        defaultMaterialDensity,
        playerMass,
        maxFall,
        massTolerance,
        draftTolerance,
        engineMaterials,
        envelopeMaterials,
        engineThrust);
  }

  private static Set<String> loadMaterialKeys(
      FileConfiguration configuration, String key, Set<String> defaults) {
    if (!configuration.isSet(key)) {
      return defaults;
    }
    Set<String> values = new HashSet<>();
    for (String value : configuration.getStringList(key)) {
      if (!value.isBlank()) {
        values.add(value.toLowerCase(Locale.ROOT));
      }
    }
    return values;
  }

  private static boolean isSailCloth(String key) {
    return key.endsWith("_wool") || key.endsWith("_banner") || key.endsWith("_wall_banner");
  }

  private static double positiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
    return value;
  }

  private static boolean isNamespacedMaterialKey(String key) {
    int colon = key.indexOf(':');
    if (colon <= 0 || colon == key.length() - 1 || colon != key.lastIndexOf(':')) {
      return false;
    }
    String namespace = key.substring(0, colon);
    String path = key.substring(colon + 1);
    return namespace.matches("[a-z0-9._-]+") && path.matches("[a-z0-9._/-]+");
  }
}
