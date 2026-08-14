package dev.jlo.ships.ship;

import dev.jlo.ships.collision.CollisionVolumeManager;
import dev.jlo.ships.model.Ship;
import java.util.Collection;

/** Transactional composition of renderer and collision runtime. */
public final class ShipRuntimeImpl implements ShipRuntime {
  /** Renderer adapter used by the runtime. */
  private final ShipRendererLike renderer;

  /** Collision adapter used by the runtime. */
  private final CollisionVolumeManager collisions;

  /**
   * Creates a transactional runtime from renderer and collision adapters.
   *
   * @param renderer renderer adapter
   * @param collisions collision adapter
   */
  public ShipRuntimeImpl(ShipRendererLike renderer, CollisionVolumeManager collisions) {
    this.renderer = renderer;
    this.collisions = collisions;
  }

  @Override
  public void spawn(Ship ship) {
    try {
      collisions.spawn(ship);
      renderer.render(ship, ignored -> {});
    } catch (ShipRuntimeException failure) {
      rollbackSpawn(ship, failure);
    }
  }

  private void rollbackSpawn(Ship ship, ShipRuntimeException failure) {
    try {
      renderer.removeRuntime(ship);
    } catch (ShipRuntimeException cleanup) {
      failure.addSuppressed(cleanup);
    }
    try {
      collisions.remove(ship.id());
    } catch (ShipRuntimeException cleanup) {
      failure.addSuppressed(cleanup);
    }
    throw failure;
  }

  @Override
  public void move(Ship ship, double oldY, double newY) {
    try {
      renderer.reposition(ship, oldY, newY);
      collisions.move(ship);
    } catch (ShipRuntimeException failure) {
      try {
        collisions.rollback(ship, oldY);
      } catch (ShipRuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      ship.setPose(new dev.jlo.ships.model.ShipPose(oldY));
      try {
        renderer.reposition(ship, newY, oldY);
      } catch (ShipRuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  @Override
  public void remove(Ship ship) {
    renderer.removeRuntime(ship);
    collisions.remove(ship.id());
  }

  @Override
  public void removeAll(Collection<Ship> ships) {
    for (Ship ship : ships) {
      remove(ship);
    }
    collisions.removeAll();
  }

  /** Removes stale plugin-owned runtimes before reconstruction. */
  public void removeAllTagged() {
    if (renderer instanceof dev.jlo.ships.bukkit.BukkitShipRenderer bukkitRenderer) {
      bukkitRenderer.removeAllRuntime();
    }
    if (collisions instanceof dev.jlo.ships.bukkit.BukkitCollisionVolumeManager bukkitCollisions) {
      bukkitCollisions.removeAllTagged();
    }
  }
}
