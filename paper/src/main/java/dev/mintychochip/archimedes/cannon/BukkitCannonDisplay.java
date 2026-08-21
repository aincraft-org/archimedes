package dev.mintychochip.archimedes.cannon;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;

/** Decodes the stable renderer identity attached to a cannon interaction hitbox. */
public final class BukkitCannonDisplay {
  private BukkitCannonDisplay() {}

  /**
   * Reads a tagged cannon interaction, if the entity carries ship and block identity.
   *
   * @param entity clicked entity
   * @param shipKey PDC key for the ship id
   * @param blockKey PDC key for the relative control
   * @return decoded target, or empty
   */
  public static Optional<DisplayTarget> read(
      Entity entity, NamespacedKey shipKey, NamespacedKey blockKey) {
    if (!(entity instanceof Interaction display)) {
      return Optional.empty();
    }
    String shipValue = display.getPersistentDataContainer().get(shipKey, PersistentDataType.STRING);
    String blockValue =
        display.getPersistentDataContainer().get(blockKey, PersistentDataType.STRING);
    if (shipValue == null || blockValue == null) {
      return Optional.empty();
    }
    try {
      String[] coordinates = blockValue.split(",", -1);
      if (coordinates.length != 3) {
        return Optional.empty();
      }
      return Optional.of(
          new DisplayTarget(
              UUID.fromString(shipValue),
              new BlockPos(
                  Integer.parseInt(coordinates[0]),
                  Integer.parseInt(coordinates[1]),
                  Integer.parseInt(coordinates[2]))));
    } catch (IllegalArgumentException failure) {
      return Optional.empty();
    }
  }

  /** Tagged ship and relative control decoded from an interaction. */
  public record DisplayTarget(UUID shipId, BlockPos relative) {}
}
