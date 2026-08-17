package dev.mintychochip.archimedes.phys.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import org.junit.jupiter.api.Test;

class BukkitMaterialKeyResolverTest {
  @Test void resolvesPlanksKey() {
    BukkitMaterialKeyResolver resolver = new BukkitMaterialKeyResolver();
    ShipBlock block = new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks");
    assertEquals("minecraft:oak_planks", resolver.key(block));
  }
}
