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
      collisions.remove(ship.id());
      throw failure;
    }
  }

  @Override
  public void move(Ship ship, double oldY, double newY) {
    renderer.reposition(ship, oldY, newY);
    try {
      collisions.move(ship);
    } catch (RuntimeException failure) {
      renderer.reposition(ship, newY, oldY);
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
}
