package dev.jlo.ships.render;

import dev.jlo.ships.model.ShipOrigin;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.NamespacedKey;

/**
 * World rendering surface the ship renderer needs, separated for unit
 * testing and forward compatibility with different Paper display APIs.
 */
public interface RenderSurface {
  /** Spawns a block display at a location with initial configuration. */
  BlockDisplay spawnBlockDisplay(Location location, java.util.function.Consumer<BlockDisplay> config);

  /** Resolves block data from its string form. */
  BlockData blockData(String serialized);

  /** Teleports an entity to an absolute location. */
  void teleport(Entity entity, Location location);

  /** Identifies the world owning this surface. */
  UUID worldUuid();

  /** Builds an absolute location for a ship block. */
  Location location(ShipOrigin origin, int dx, int dy, int dz);

  /** Notifies the surface of a newly rendered ship. */
  void shipRendered(UUID shipId, java.util.Collection<BlockDisplay> displays);

  /** Removes every entity carrying the ship identifier in its tag. */
  void removeTagged(NamespacedKey key, String shipId);

  /** Wraps a Bukkit world. */
  static RenderSurface of(World world) {
    return new RenderSurface() {
      @Override
      public BlockDisplay spawnBlockDisplay(Location location, java.util.function.Consumer<BlockDisplay> config) {
        return world.spawn(location, BlockDisplay.class, config::accept);
      }

      @Override
      public BlockData blockData(String serialized) {
        return org.bukkit.Bukkit.createBlockData(serialized);
      }

      @Override
      public void teleport(org.bukkit.entity.Entity entity, Location location) {
        entity.teleport(location);
      }

      @Override
      public UUID worldUuid() {
        return world.getUID();
      }

      @Override
      public Location location(ShipOrigin origin, int dx, int dy, int dz) {
        return new Location(world, origin.x() + dx + 0.5, origin.y() + dy + 0.5, origin.z() + dz + 0.5);
      }

      @Override
      public void shipRendered(UUID shipId, java.util.Collection<BlockDisplay> displays) {
        // Rendered displays are tracked in their tags and removed by tag.
      }

      @Override
      public void removeTagged(NamespacedKey key, String shipId) {
        for (org.bukkit.entity.Entity entity :
            world.getEntitiesByClass(BlockDisplay.class)) {
          String tag =
              entity
                  .getPersistentDataContainer()
                  .get(key, org.bukkit.persistence.PersistentDataType.STRING);
          if (shipId.equals(tag)) {
            entity.remove();
          }
        }
      }
    };
  }
}