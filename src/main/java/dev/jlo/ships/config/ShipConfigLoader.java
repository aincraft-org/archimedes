package dev.jlo.ships.config;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;

/** Builds a validated {@link ShipConfig} from Bukkit configuration values. */
public final class ShipConfigLoader {
  /** Key for the maximum assembled blocks setting. */
  public static final String MAXIMUM_BLOCKS_KEY = "maximum-blocks";
  /** Key for the command targeting distance setting. */
  public static final String TARGET_DISTANCE_KEY = "target-distance";
  /** Key for the forbidden material list setting. */
  public static final String FORBIDDEN_MATERIALS_KEY = "forbidden-materials";
  /** Key for the disabled world identifier list setting. */
  public static final String DISABLED_WORLDS_KEY = "disabled-worlds";

  private ShipConfigLoader() {}

  /**
   * Builds a configuration, throwing {@link IllegalArgumentException} when a
   * value is missing or unsafe.
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
        forbidden.add(value.toLowerCase(java.util.Locale.ROOT));
      }
    }
    Set<UUID> disabledWorlds = new HashSet<>();
    for (String value : configuration.getStringList(DISABLED_WORLDS_KEY)) {
      try {
        disabledWorlds.add(UUID.fromString(value));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("invalid world id in disabled-worlds: " + value);
      }
    }
    return new ShipConfig(maximumBlocks, targetDistance, forbidden, disabledWorlds);
  }
}