package dev.jlo.ships.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Validation tests for the ship configuration loader. */
class ShipConfigLoaderTest {
  @Test
  void loadsValidConfiguration() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 500);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set(
        ShipConfigLoader.FORBIDDEN_MATERIALS_KEY,
        java.util.List.of("minecraft:water", "minecraft:lava"));
    ShipConfig loaded = ShipConfigLoader.load(config);
    assertEquals(500, loaded.maximumBlocks());
    assertEquals(8, loaded.targetDistance());
    assertTrue(loaded.forbiddenMaterials().contains("minecraft:water"));
  }

  @Test
  void rejectsNonPositiveMaximum() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 0);
    assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(config));
  }

  @Test
  void rejectsInvalidDisabledWorld() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set(ShipConfigLoader.DISABLED_WORLDS_KEY, java.util.List.of("not-a-uuid"));
    assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(config));
  }

  @Test
  void permitsAllWorldsByDefault() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    ShipConfig loaded = ShipConfigLoader.load(config);
    assertTrue(loaded.worldEnabled(java.util.UUID.randomUUID()));
  }
}
