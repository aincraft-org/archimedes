package dev.mintychochip.archimedes.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.cannon.BukkitCannonDisplay.DisplayTarget;
import dev.mintychochip.archimedes.model.BlockPos;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

/** Tests strict renderer identity decoding. */
class BukkitCannonDisplayTest {
  private static final NamespacedKey SHIP = new NamespacedKey("archimedes", "ship-id");
  private static final NamespacedKey BLOCK = new NamespacedKey("archimedes", "ship-id-block");

  @Test
  void decodesTaggedInteraction() {
    UUID id = UUID.randomUUID();
    Interaction display = display(id.toString(), "2,1,-3");

    assertEquals(
        Optional.of(new DisplayTarget(id, new BlockPos(2, 1, -3))),
        BukkitCannonDisplay.read(display, SHIP, BLOCK));
  }

  @Test
  void rejectsMissingMalformedAndNonInteractionIdentity() {
    assertTrue(BukkitCannonDisplay.read(entity(), SHIP, BLOCK).isEmpty());
    assertTrue(BukkitCannonDisplay.read(display(null, "0,0,0"), SHIP, BLOCK).isEmpty());
    assertTrue(BukkitCannonDisplay.read(display("not-a-uuid", "0,0,0"), SHIP, BLOCK).isEmpty());
    assertTrue(
        BukkitCannonDisplay.read(display(UUID.randomUUID().toString(), "0,0"), SHIP, BLOCK)
            .isEmpty());
  }

  private static Interaction display(String ship, String block) {
    Map<NamespacedKey, String> tags = new HashMap<>();
    tags.put(SHIP, ship);
    tags.put(BLOCK, block);
    PersistentDataContainer data =
        (PersistentDataContainer)
            Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, args) -> "get".equals(method.getName()) ? tags.get(args[0]) : null);
    return (Interaction)
        Proxy.newProxyInstance(
            Interaction.class.getClassLoader(),
            new Class<?>[] {Interaction.class},
            (proxy, method, args) ->
                "getPersistentDataContainer".equals(method.getName())
                    ? data
                    : defaultValue(method.getReturnType()));
  }

  private static Entity entity() {
    return (Entity)
        Proxy.newProxyInstance(
            Entity.class.getClassLoader(),
            new Class<?>[] {Entity.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
  }

  private static Object defaultValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == double.class) {
      return 0.0;
    }
    return null;
  }
}
