package dev.mintychochip.archimedes.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ShipConfigPhysicsTest {
  @Test
  void loadsMaterialDensitiesAndTolerances() {
    YamlConfiguration cfg = new YamlConfiguration();
    cfg.set("maximum-blocks", 10);
    cfg.set("target-distance", 16);
    cfg.set("forbidden-materials", java.util.List.of());
    cfg.set("disabled-worlds", java.util.List.of());
    cfg.set("buoyancy-enabled", true);
    cfg.set("physics-ticks", 1);
    cfg.set("bob-amplitude", 0.5);
    cfg.set("max-rise", 16.0);
    cfg.set("gravity", 0.05);
    cfg.set("water-density", 1.0);
    cfg.set("block-density", 0.5);
    cfg.set("damping", 0.9);
    cfg.set("buoyancy.material-densities.minecraft:oak_planks", 0.6);
    cfg.set("buoyancy.default-material-density", 1.0);
    cfg.set("buoyancy.player-mass", 80.0);
    cfg.set("buoyancy.max-fall", 16.0);
    cfg.set("buoyancy.mass-tolerance", 1e-6);
    cfg.set("buoyancy.draft-tolerance", 1e-3);
    ShipConfig config = ShipConfigLoader.load(cfg);
    assertEquals(0.6, config.materialDensities().get("minecraft:oak_planks"), 1e-9);
    assertEquals(1.0, config.defaultMaterialDensity(), 1e-9);
    assertEquals(80.0, config.playerMass(), 1e-9);
    assertEquals(16.0, config.maxFall(), 1e-9);
    assertEquals(1e-6, config.massTolerance(), 1e-12);
    assertEquals(1e-3, config.draftTolerance(), 1e-12);
  }
}
