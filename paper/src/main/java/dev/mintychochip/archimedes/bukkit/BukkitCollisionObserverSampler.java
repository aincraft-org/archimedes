package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.collision.CollisionBox;
import dev.mintychochip.archimedes.collision.CollisionObserver;
import dev.mintychochip.archimedes.collision.ExposedCellIndex;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * Collects nearby entities that may need streamed hull cubes, skipping ship-owned collision and
 * render entities.
 */
public final class BukkitCollisionObserverSampler {
  /** World containing candidate entities. */
  private final World world;

  /** Persistent key identifying collision volumes. */
  private final NamespacedKey collisionOwnerKey;

  /** Persistent key identifying render displays. */
  private final NamespacedKey renderShipKey;

  /**
   * Creates a sampler for the supplied world and identity keys.
   *
   * @param world world containing entities
   * @param collisionOwnerKey collision-owner key
   * @param renderShipKey render-owner key
   */
  public BukkitCollisionObserverSampler(
      World world, NamespacedKey collisionOwnerKey, NamespacedKey renderShipKey) {
    this.world = world;
    this.collisionOwnerKey = collisionOwnerKey;
    this.renderShipKey = renderShipKey;
  }

  /**
   * Returns observers whose boxes intersect the hull bounds expanded by the leave range.
   *
   * @param ship ship being observed
   * @param index exposed-cell index for {@code ship}
   * @return observers in range
   */
  public List<CollisionObserver> sample(Vehicle ship, ExposedCellIndex index) {
    CollisionBox bounds = index.bounds(ship.pose().x(), ship.pose().y(), ship.pose().z());
    if (bounds == null) {
      return List.of();
    }
    CollisionBox query = bounds.expanded(ExposedCellIndex.LEAVE_RANGE);
    BoundingBox nearby =
        new BoundingBox(
            query.minX(), query.minY(), query.minZ(), query.maxX(), query.maxY(), query.maxZ());
    Collection<Entity> nearbyEntities = world.getNearbyEntities(nearby);
    if (nearbyEntities == null) {
      return List.of();
    }
    List<CollisionObserver> observers = new ArrayList<>();
    for (Entity entity : nearbyEntities) {
      if (isShipOwned(entity)) {
        continue;
      }
      BoundingBox box = entity.getBoundingBox();
      observers.add(
          new CollisionObserver(
              entity.getUniqueId(),
              entity instanceof Player,
              new CollisionBox(
                  box.getMinX(),
                  box.getMinY(),
                  box.getMinZ(),
                  box.getMaxX(),
                  box.getMaxY(),
                  box.getMaxZ())));
    }
    return List.copyOf(observers);
  }

  private boolean isShipOwned(Entity entity) {
    String collision =
        entity.getPersistentDataContainer().get(collisionOwnerKey, PersistentDataType.STRING);
    if (collision != null && !collision.isEmpty()) {
      return true;
    }
    String render =
        entity.getPersistentDataContainer().get(renderShipKey, PersistentDataType.STRING);
    return render != null && !render.isEmpty();
  }
}
