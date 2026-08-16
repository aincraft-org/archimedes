package dev.jlo.ships.render;

import dev.jlo.ships.ship.ShipRuntimeException;
import dev.jlo.ships.model.ShipOrigin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * World rendering surface the ship renderer needs, separated for unit testing and forward
 * compatibility with different Paper display APIs.
 */
public interface RenderSurface {
  /**
   * Spawns a block display at a location with initial configuration.
   *
   * @param location the spawn location
   * @param config the initial configuration consumer
   * @return the spawned block display
   */
  BlockDisplay spawnBlockDisplay(
      Location location, java.util.function.Consumer<BlockDisplay> config);

  /**
   * Resolves block data from its string form.
   *
   * @param serialized the serialized block data
   * @return the resolved block data
   */
  BlockData blockData(String serialized);

  /**
   * Teleports an entity to an absolute location.
   *
   * @param entity the entity to teleport
   * @param location the destination location
   */
  void teleport(Entity entity, Location location);

  /**
   * @return the world owning this surface
   */
  UUID worldUuid();

  /**
   * Builds an absolute location for a ship block.
   *
   * @param origin the ship origin
   * @param dx the relative x offset
   * @param dy the relative y offset (fractional, includes pose)
   * @param dz the relative z offset
   * @return the absolute location
   */
  Location location(ShipOrigin origin, double dx, double dy, double dz);

  /**
   * Notifies the surface of a newly rendered ship.
   *
   * @param shipId the ship identifier
   * @param displays the spawned displays
   */
  void shipRendered(UUID shipId, Collection<BlockDisplay> displays);

  /**
   * Removes every entity carrying the ship identifier in its tag.
   *
   * @param key the tag key
   * @param shipId the ship identifier
   */
  void removeTagged(NamespacedKey key, String shipId);

  /**
   * Returns every entity carrying the ship identifier in its tag.
   *
   * @param key the tag key
   * @param shipId the ship identifier
   * @return the tagged displays
   */
  Collection<BlockDisplay> tagged(NamespacedKey key, String shipId);

  /**
   * Removes every display carrying the supplied plugin tag.
   *
   * @param key the plugin-owned tag key
   */
  default void removeAllTagged(NamespacedKey key) {}

  /**
   * Wraps a Bukkit world.
   *
   * @param world the Bukkit world
   * @return a render surface over the world
   */
  static RenderSurface of(World world) {
    return new RenderSurface() {
      @Override
      public BlockDisplay spawnBlockDisplay(
          Location location, java.util.function.Consumer<BlockDisplay> config) {
        try {
          return world.spawn(location, BlockDisplay.class, config::accept);
        } catch (IllegalArgumentException failure) {
          throw new dev.jlo.ships.ship.ShipRuntimeException(failure);
        }
      }

      @Override
      public BlockData blockData(String serialized) {
        try {
          return org.bukkit.Bukkit.createBlockData(serialized);
        } catch (IllegalArgumentException failure) {
          throw new dev.jlo.ships.ship.ShipRuntimeException(failure);
        }
      }

      @Override
      public void teleport(Entity entity, Location location) {
        try {
          if (!entity.teleport(location)) {
            throw new dev.jlo.ships.ship.ShipRuntimeException(
                new IllegalStateException("Display teleport returned false"));
          }
        } catch (dev.jlo.ships.ship.ShipRuntimeException failure) {
          throw failure;
        } catch (IllegalArgumentException failure) {
          throw new dev.jlo.ships.ship.ShipRuntimeException(failure);
        }
      }

      @Override
      public UUID worldUuid() {
        return world.getUID();
      }

      @Override
      public Location location(ShipOrigin origin, double dx, double dy, double dz) {
        return new Location(world, origin.x() + dx, origin.y() + dy, origin.z() + dz);
      }

      @Override
      public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {
        // Rendered displays are tracked in their tags and removed by tag.
      }

      @Override
      public void removeTagged(NamespacedKey key, String shipId) {
        ShipRuntimeException failure = null;
        try {
          for (Entity entity : world.getEntitiesByClass(BlockDisplay.class)) {
            try {
              String tag =
                  entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
              if (shipId.equals(tag)) {
                entity.remove();
              }
            } catch (RuntimeException cleanup) {
              ShipRuntimeException normalized = normalize("remove", shipId, cleanup);
              if (failure == null) {
                failure = normalized;
              } else {
                failure.addSuppressed(normalized);
              }
            }
          }
        } catch (RuntimeException enumeration) {
          throw normalize("remove", shipId, enumeration);
        }
        if (failure != null) {
          throw failure;
        }
      }

      @Override
      public Collection<BlockDisplay> tagged(NamespacedKey key, String shipId) {
        final List<BlockDisplay> found = new ArrayList<>();
        for (Entity entity : world.getEntitiesByClass(BlockDisplay.class)) {
          String tag = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
          if (shipId.equals(tag)) {
            found.add((BlockDisplay) entity);
          }
        }
        return found;
      }

      @Override
      public void removeAllTagged(NamespacedKey key) {
        ShipRuntimeException failure = null;
        try {
          for (Entity entity : world.getEntitiesByClass(BlockDisplay.class)) {
            try {
              if (entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                entity.remove();
              }
            } catch (RuntimeException cleanup) {
              ShipRuntimeException normalized = normalize("removeAll", null, cleanup);
              if (failure == null) {
                failure = normalized;
              } else {
                failure.addSuppressed(normalized);
              }
            }
          }
        } catch (RuntimeException enumeration) {
          throw normalize("removeAll", null, enumeration);
        }
        if (failure != null) {
          throw failure;
        }
      }

      private ShipRuntimeException normalize(
          String operation, String shipId, RuntimeException failure) {
        if (failure instanceof ShipRuntimeException) {
          return (ShipRuntimeException) failure;
        }
        String context = shipId == null ? operation + " failed" : operation + " failed for ship " + shipId;
        return new ShipRuntimeException(new IllegalStateException(context, failure));
      }
    };
  }
}
