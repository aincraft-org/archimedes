package dev.jlo.ships.ship;

import dev.jlo.ships.collision.CollisionVolumeManager;
import dev.jlo.ships.model.Ship;
import java.util.Collection;

/** Transactional composition of renderer and collision runtime. */
public final class ShipRuntimeImpl implements ShipRuntime {
  private final ShipRendererLike renderer;
  private final CollisionVolumeManager collisions;

  public ShipRuntimeImpl(ShipRendererLike renderer, CollisionVolumeManager collisions) {
    this.renderer = renderer;
    this.collisions = collisions;
  }

  @Override
  public void spawn(Ship ship) {
    collisions.spawn(ship);
    try {
      renderer.render(ship, ignored -> {});
    } catch (RuntimeException failure) {
      try {
        renderer.removeRuntime(ship);
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      try {
        collisions.remove(ship.id());
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  @Override
  public void move(Ship ship, double oldY, double newY) {
    try {
      renderer.reposition(ship, oldY, newY);
      collisions.move(ship);
    } catch (RuntimeException failure) {
      try {
        collisions.rollback(ship, oldY);
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      ship.setPose(new dev.jlo.ships.model.ShipPose(oldY));
      try {
        renderer.reposition(ship, newY, oldY);
      } catch (RuntimeException cleanup) {
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
