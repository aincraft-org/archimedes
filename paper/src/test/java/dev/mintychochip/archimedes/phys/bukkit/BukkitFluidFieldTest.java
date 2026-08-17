package dev.mintychochip.archimedes.phys.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import dev.mintychochip.phys.Vector3;
import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class BukkitFluidFieldTest {
  @Test void waterIsFluidWithConfiguredDensity() {
    Block block = (Block) Proxy.newProxyInstance(
        Block.class.getClassLoader(),
        new Class<?>[] {Block.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getType")) return Material.WATER;
          return null;
        });
    World world = (World) Proxy.newProxyInstance(
        World.class.getClassLoader(),
        new Class<?>[] {World.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getBlockAt") && args != null && args.length == 3
              && (int) args[0] == 0 && (int) args[1] == 10 && (int) args[2] == 0) {
            return block;
          }
          return null;
        });
    BukkitFluidField field = new BukkitFluidField(world, 1000.0);
    assertTrue(field.isFluid(new Vector3(0.5, 10.5, 0.5)));
    assertEquals(1000.0, field.density(new Vector3(0.5, 10.5, 0.5)), 1e-9);
  }
}
