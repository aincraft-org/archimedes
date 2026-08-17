package dev.mintychochip.archimedes.command;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BukkitTargetResolverTest {
  @Test
  void rejectsNullTarget() {
    assertNull(new BukkitTargetResolver(5).resolve(player(null)));
  }

  private static Player player(Block block) {
    return (Player)
        Proxy.newProxyInstance(
            BukkitTargetResolverTest.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (p, method, args) -> method.getName().equals("getTargetBlockExact") ? block : null);
  }
}
