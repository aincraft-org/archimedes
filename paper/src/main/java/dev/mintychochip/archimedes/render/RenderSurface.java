package dev.mintychochip.archimedes.render;

import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.ship.ShipRuntimeException;
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
@SuppressWarnings({
  "checkstyle:IllegalCatch",
  "PMD.AvoidCatchingGenericException",
  "PMD.AvoidDuplicateLiterals"
})
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
   * Spawns an invisible interaction hitbox at a location.
   *
   * @param location spawn location
   * @param config initial configuration
   * @return spawned interaction entity
   */
  default org.bukkit.entity.Interaction spawnInteraction(
      Location location, java.util.function.Consumer<org.bukkit.entity.Interaction> config) {
    throw new UnsupportedOperationException("Interaction hitboxes are not supported");
  }

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
   * Returns interaction hitboxes carrying the supplied ship tag.
   *
   * @param key ship tag key
   * @param shipId ship identifier
   * @return matching interaction hitboxes
   */
  default Collection<org.bukkit.entity.Interaction> taggedInteractions(
      NamespacedKey key, String shipId) {
    return List.of();
  }

  /**
   * Returns every entity carrying the ship identifier in its tag.
   *
   * @param key the tag key
   * @param shipId the ship identifier
   * @return the tagged displays
   */
  Collection<BlockDisplay> tagged(NamespacedKey key, String shipId);

  /**
   * Removes every display carrying the supplied plugin tag when supported by the surface.
   *
   * <p>The default implementation is a no-op; surfaces that support bulk tagged removal may
   * override it.
   *
   * @param key the plugin-owned tag key
   */
  default void removeAllTagged(NamespacedKey key) {}

  /**
   * A player eye that may receive display spawn packets.
   *
   * @param id player id
   * @param eyeX eye x
   * @param eyeY eye y
   * @param eyeZ eye z
   */
  record Viewer(UUID id, double eyeX, double eyeY, double eyeZ) {}

  /**
   * Returns nearby player eyes that should receive display packets.
   *
   * @return viewers
   */
  default Collection<Viewer> viewers() {
    return List.of();
  }

  /**
   * Shows {@code entity} to the viewer.
   *
   * @param viewerId player id
   * @param entity display
   */
  default void showTo(UUID viewerId, Entity entity) {}

  /**
   * Hides {@code entity} from the viewer.
   *
   * @param viewerId player id
   * @param entity display
   */
  default void hideFrom(UUID viewerId, Entity entity) {}

  /**
   * Returns whether the world cell is a solid occluder.
   *
   * @param x world x
   * @param y world y
   * @param z world z
   * @return {@code true} when solid
   */
  default boolean worldSolid(int x, int y, int z) {
    return false;
  }

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
          throw new dev.mintychochip.archimedes.ship.ShipRuntimeException(failure);
        }
      }

      @Override
      public org.bukkit.entity.Interaction spawnInteraction(
          Location location, java.util.function.Consumer<org.bukkit.entity.Interaction> config) {
        return world.spawn(location, org.bukkit.entity.Interaction.class, config::accept);
      }

      @Override
      public BlockData blockData(String serialized) {
        try {
          return org.bukkit.Bukkit.createBlockData(serialized);
        } catch (IllegalArgumentException failure) {
          throw new dev.mintychochip.archimedes.ship.ShipRuntimeException(failure);
        }
      }

      @Override
      public void teleport(Entity entity, Location location) {
        try {
          if (!entity.teleport(location)) {
            throw new dev.mintychochip.archimedes.ship.ShipRuntimeException(
                new IllegalStateException("Display teleport returned false"));
          }
        } catch (dev.mintychochip.archimedes.ship.ShipRuntimeException failure) {
          throw failure;
        } catch (IllegalArgumentException failure) {
          throw new dev.mintychochip.archimedes.ship.ShipRuntimeException(failure);
        }
      }

      @Override
      public UUID worldUuid() {
        return world.getUID();
      }

      @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
      @Override
      public Location location(ShipOrigin origin, double dx, double dy, double dz) {
        return new Location(world, origin.x() + dx, origin.y() + dy, origin.z() + dz);
      }

      @Override
      public void shipRendered(UUID shipId, Collection<BlockDisplay> displays) {
        // Runtime entities are tracked through persistent data tags.
      }

      @Override
      @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
      public void removeTagged(NamespacedKey key, String shipId) {
        ShipRuntimeException failure = null;
        try {
          for (Entity entity : world.getEntities()) {
            try {
              String tag = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
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
        List<BlockDisplay> found = new ArrayList<>();
        for (Entity entity : world.getEntitiesByClass(BlockDisplay.class)) {
          String tag = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
          if (shipId.equals(tag)) {
            found.add((BlockDisplay) entity);
          }
        }
        return found;
      }

      @Override
      public Collection<org.bukkit.entity.Interaction> taggedInteractions(
          NamespacedKey key, String shipId) {
        List<org.bukkit.entity.Interaction> found = new ArrayList<>();
        for (Entity entity : world.getEntitiesByClass(org.bukkit.entity.Interaction.class)) {
          String tag = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
          if (shipId.equals(tag)) {
            found.add((org.bukkit.entity.Interaction) entity);
          }
        }
        return found;
      }

      @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
      @Override
      public void removeAllTagged(NamespacedKey key) {
        ShipRuntimeException failure = null;
        try {
          for (Entity entity : world.getEntities()) {
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

      @Override
      public Collection<Viewer> viewers() {
        List<Viewer> eyes = new ArrayList<>();
        for (org.bukkit.entity.Player player : world.getPlayers()) {
          org.bukkit.Location eye = player.getEyeLocation();
          eyes.add(new Viewer(player.getUniqueId(), eye.getX(), eye.getY(), eye.getZ()));
        }
        return eyes;
      }

      @Override
      public void showTo(UUID viewerId, Entity entity) {
        org.bukkit.entity.Player player = player(viewerId);
        org.bukkit.plugin.Plugin plugin = plugin();
        if (player != null && plugin != null) {
          player.showEntity(plugin, entity);
        }
      }

      @Override
      public void hideFrom(UUID viewerId, Entity entity) {
        org.bukkit.entity.Player player = player(viewerId);
        org.bukkit.plugin.Plugin plugin = plugin();
        if (player != null && plugin != null) {
          player.hideEntity(plugin, entity);
        }
      }

      private org.bukkit.entity.Player player(UUID viewerId) {
        for (org.bukkit.entity.Player player : world.getPlayers()) {
          if (player.getUniqueId().equals(viewerId)) {
            return player;
          }
        }
        return null;
      }

      @Override
      public boolean worldSolid(int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
          return false;
        }
        return world.getBlockAt(x, y, z).getType().isSolid();
      }

      private org.bukkit.plugin.Plugin plugin() {
        try {
          return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(RenderSurface.class);
        } catch (IllegalArgumentException ignored) {
          return null;
        }
      }

      private ShipRuntimeException normalize(
          String operation, String shipId, RuntimeException failure) {
        if (failure instanceof ShipRuntimeException) {
          return (ShipRuntimeException) failure;
        }
        String context =
            shipId == null ? operation + " failed" : operation + " failed for ship " + shipId;
        return new ShipRuntimeException(new IllegalStateException(context, failure));
      }
    };
  }
}
