package dev.mintychochip.archimedes.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Validation tests for the ship configuration loader. */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
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
  void normalizesForbiddenMaterialsAndDropsBlankEntries() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set(
        ShipConfigLoader.FORBIDDEN_MATERIALS_KEY,
        java.util.List.of("STONE", " ", "minecraft:LAVA"));

    ShipConfig loaded = ShipConfigLoader.load(config);

    assertEquals(java.util.Set.of("stone", "minecraft:lava"), loaded.forbiddenMaterials());
  }

  @Test
  void usesDocumentedDefaultsForMissingOptionalKeys() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);

    ShipConfig loaded = ShipConfigLoader.load(config);

    assertTrue(loaded.buoyancyEnabled());
    assertEquals(1, loaded.physicsTicks());
    assertEquals(0.5, loaded.bobAmplitude());
    assertEquals(16.0, loaded.maxRise());
    assertEquals(10.0, loaded.gravity());
    assertEquals(1.0, loaded.waterDensity());
    assertEquals(0.5, loaded.blockDensity());
    assertEquals(0.9, loaded.damping());
  }

  @Test
  void rejectsNonFiniteRangeValue() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set(ShipConfigLoader.DAMPING_KEY, Double.NaN);

    assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(config));
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

  @Test
  void loadsBuoyancySettings() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set(ShipConfigLoader.BUOYANCY_ENABLED_KEY, false);
    config.set(ShipConfigLoader.PHYSICS_TICKS_KEY, 2);
    config.set(ShipConfigLoader.BOB_AMPLITUDE_KEY, 0.75);
    config.set(ShipConfigLoader.MAX_RISE_KEY, 20.0);
    config.set(ShipConfigLoader.GRAVITY_KEY, 0.05);
    config.set(ShipConfigLoader.WATER_DENSITY_KEY, 1.0);
    config.set(ShipConfigLoader.BLOCK_DENSITY_KEY, 0.5);
    config.set(ShipConfigLoader.DAMPING_KEY, 0.9);
    ShipConfig loaded = ShipConfigLoader.load(config);
    assertFalse(loaded.buoyancyEnabled());
    assertEquals(2, loaded.physicsTicks());
    assertEquals(0.75, loaded.bobAmplitude());
    assertEquals(20.0, loaded.maxRise());
    assertEquals(0.05, loaded.gravity());
    assertEquals(1.0, loaded.waterDensity());
    assertEquals(0.5, loaded.blockDensity());
    assertEquals(0.9, loaded.damping());
  }

  @Test
  void rejectsDuplicateMaterialDensitiesAfterNormalization() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set("buoyancy.material-densities.Minecraft:oak_planks", 0.6);
    config.set("buoyancy.material-densities.minecraft:OAK_PLANKS", 0.7);
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(config));
    assertTrue(thrown.getMessage().toLowerCase().contains("minecraft:oak_planks"));
  }

  @Test
  void a4NormalizesMixedCaseAndPaddedMaterialKeys() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set("buoyancy.material-densities.  minecraft:Oak_Planks  ", 0.6);
    ShipConfig loaded = ShipConfigLoader.load(config);
    assertEquals(0.6, loaded.materialDensities().get("minecraft:oak_planks"), 1e-9);
  }

  @Test
  void a5RejectsZeroDensity() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set("buoyancy.material-densities.minecraft:oak_planks", 0.0);
    assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(config));
  }

  @Test
  void a5RejectsNegativeDensity() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set("buoyancy.material-densities.minecraft:oak_planks", -1.0);
    assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(config));
  }

  @Test
  void a5RejectsMalformedMaterialKey() {
    YamlConfiguration config = new YamlConfiguration();
    config.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    config.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    config.set("buoyancy.material-densities.not_a_key", 0.6);
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(config));
    assertTrue(thrown.getMessage().contains("not_a_key"));
  }

  @Test
  void a17InvalidReloadLeavesPriorConfigActive() {
    YamlConfiguration prior = new YamlConfiguration();
    prior.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    prior.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    prior.set("buoyancy.material-densities.minecraft:oak_planks", 0.6);
    ShipConfig valid = ShipConfigLoader.load(prior);

    YamlConfiguration broken = new YamlConfiguration();
    broken.set(ShipConfigLoader.MAXIMUM_BLOCKS_KEY, 100);
    broken.set(ShipConfigLoader.TARGET_DISTANCE_KEY, 8);
    broken.set("buoyancy.material-densities.minecraft:oak_planks", -0.6);
    assertThrows(IllegalArgumentException.class, () -> ShipConfigLoader.load(broken));

    assertEquals(0.6, valid.materialDensities().get("minecraft:oak_planks"), 1e-9);
  }
}
