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

  /** Optional carrier for non-ship entities standing on the ship. */
  private final ShipEntityCarrier carrier;

  /**
   * Creates a transactional runtime from renderer and collision adapters.
   *
   * @param renderer renderer adapter
   * @param collisions collision adapter
   */
  public ShipRuntimeImpl(ShipRendererLike renderer, CollisionVolumeManager collisions) {
    this(renderer, collisions, NoopShipEntityCarrier.INSTANCE);
  }

  /**
   * Creates a transactional runtime with an entity carrier.
   *
   * @param renderer renderer adapter
   * @param collisions collision adapter
   * @param carrier carrier for non-ship entities
   */
  public ShipRuntimeImpl(
      ShipRendererLike renderer, CollisionVolumeManager collisions, ShipEntityCarrier carrier) {
    this.renderer = renderer;
    this.collisions = collisions;
    this.carrier = carrier;
  }

  @Override
  public void spawn(Ship ship) {
    boolean rendererStarted = false;
    try {
      collisions.spawn(ship);
      rendererStarted = true;
      renderer.render(ship, ignored -> {});
      carrier.track(ship, ship.pose().y());
    } catch (ShipRuntimeException failure) {
      if (rendererStarted) {
        try {
          renderer.removeRuntime(ship);
        } catch (ShipRuntimeException cleanup) {
          failure.addSuppressed(cleanup);
        }
      }
      try {
        collisions.remove(ship.id());
      } catch (ShipRuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      try {
        carrier.untrack(ship);
      } catch (ShipRuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  @Override
  public void move(Ship ship, double oldY, double newY) {
    boolean rising = newY > oldY;
    boolean rendererStarted = false;
    boolean carrierStarted = false;
    boolean collisionsStarted = false;
    try {
      rendererStarted = true;
      renderer.reposition(ship, oldY, newY);
      if (rising) {
        carrierStarted = true;
        carrier.carry(ship, oldY, newY);
      }
      collisionsStarted = true;
      collisions.move(ship);
      if (!rising) {
        carrierStarted = true;
        carrier.carry(ship, oldY, newY);
      }
      carrier.updatePoseBasis(ship, newY);
    } catch (ShipRuntimeException failure) {
      if (collisionsStarted) {
        try {
          collisions.rollback(ship, oldY);
        } catch (ShipRuntimeException cleanup) {
          failure.addSuppressed(cleanup);
        }
      }
      ship.setPose(new dev.jlo.ships.model.ShipPose(oldY));
      if (rising && carrierStarted) {
        try {
          carrier.carry(ship, newY, oldY);
        } catch (ShipRuntimeException cleanup) {
          failure.addSuppressed(cleanup);
        }
      }
      if (carrierStarted) {
        carrier.updatePoseBasis(ship, oldY);
      }
      if (rendererStarted) {
        try {
          renderer.reposition(ship, newY, oldY);
        } catch (ShipRuntimeException cleanup) {
          failure.addSuppressed(cleanup);
        }
      }
      throw failure;
    }
  }

  @Override
  public void remove(Ship ship) {
    ShipRuntimeException failure = null;
    try {
      renderer.removeRuntime(ship);
      collisions.remove(ship.id());
    } catch (ShipRuntimeException current) {
      failure = current;
    } finally {
      try {
        carrier.untrack(ship);
      } catch (ShipRuntimeException cleanup) {
        if (failure == null) {
          failure = cleanup;
        } else {
          failure.addSuppressed(cleanup);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  @Override
  public void removeAll(Collection<Ship> ships) {
    try {
      for (Ship ship : ships) {
        remove(ship);
      }
      collisions.removeAll();
    } finally {
      carrier.clear();
    }
  }

  public void removeAllTagged() {
    try {
      renderer.removeAllRuntime();
      collisions.removeAllTagged();
    } finally {
      carrier.clear();
    }
  }
}
