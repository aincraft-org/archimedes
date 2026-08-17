package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.GravityForce;
import dev.mintychochip.phys.Physics;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.World;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3d;

/**
 * Default buoyancy controller that combines force integration with runtime movement.
 *
 * <p>Movement is path-checked and rolled back when the runtime cannot apply it. Vertical velocity
 * is retained per ship between ticks and cleared when movement is explicitly reset.
 */
public final class ShipPhysicsImpl implements ShipPhysics {
  /** Generic stateless integrator. */
  private final Physics physics;

  /** Physics world adapter. */
  private final World world;

  /** Ship physics configuration. */
  private final ShipConfig config;

  /** Block material key resolver. */
  private final MaterialKeyResolver resolver;

  /** Runtime movement coordinator. */
  private final ShipRuntime runtime;

  /** Rider count provider. */
  private final RiderCount riderCount;

  /** Per-ship vertical velocities. */
  private final Map<UUID, Double> velocities = new HashMap<>();

  /**
   * Creates a ship physics facade.
   *
   * @param physics generic physics integrator
   * @param world physics world
   * @param config ship configuration
   * @param resolver block material resolver
   * @param runtime runtime movement coordinator
   * @param riderCount rider count provider
   */
  public ShipPhysicsImpl(
      Physics physics,
      World world,
      ShipConfig config,
      MaterialKeyResolver resolver,
      ShipRuntime runtime,
      RiderCount riderCount) {
    this.physics = physics;
    this.world = world;
    this.config = config;
    this.resolver = resolver;
    this.runtime = runtime;
    this.riderCount = riderCount;
  }

  /**
   * Advances one buoyancy tick by stepping the attached gravity and buoyancy forces.
   *
   * @param ship ship to update
   * @return whether a meaningful movement was committed
   */
  @Override
  public boolean tick(Ship ship) {
    if (!ship.buoyancyEnabled()) return false;
    return integrate(ship, 1);
  }

  /**
   * Steps the engine until vertical motion settles, then commits the pose.
   *
   * @param ship ship to raise
   * @return whether the move was committed; no water or disabled buoyancy is handled explicitly
   */
  @Override
  public boolean rise(Ship ship) {
    if (!ship.buoyancyEnabled()) return true;
    Body probe = body(ship);
    if (WaterlineResolver.submergedVolume(probe, world) == 0) return false;
    velocities.put(ship.id(), 0.0);
    return integrate(ship, 80);
  }

  /**
   * Moves directly downward by the requested number of blocks, clamped to the fall limit.
   *
   * @param ship ship to sink
   * @param blocks positive requested distance
   * @return whether movement was committed
   */
  @Override
  public boolean sink(Ship ship, int blocks) {
    if (!ship.buoyancyEnabled() || blocks <= 0) return false;
    double oldY = ship.pose().y();
    double targetY = Math.max(-config.maxFall(), oldY - blocks);
    return moveDirect(ship, oldY, targetY);
  }

  /**
   * Removes the ship's retained vertical velocity.
   *
   * @param ship ship whose transient state is cleared
   */
  @Override
  public void clear(Ship ship) {
    velocities.remove(ship.id());
  }

  private Body body(Ship ship) {
    return ShipBody.from(
        ship,
        resolver,
        config,
        riderCount.count(ship),
        new GravityForce(),
        new ShipBuoyancyForce());
  }

  private boolean integrate(Ship ship, int steps) {
    double oldY = ship.pose().y();
    Body body = body(ship);
    body.setLinearVelocity(new Vector3d(0, velocities.getOrDefault(ship.id(), 0.0), 0));
    double newY = oldY;
    for (int i = 0; i < steps; i++) {
      physics.step(world, List.of(body));
      newY = clamp(ship, oldY, body);
      if (Math.abs(body.linearVelocity().y()) < config.draftTolerance()) {
        break;
      }
    }
    velocities.put(ship.id(), body.linearVelocity().y() * config.damping());
    if (Math.abs(newY - oldY) < config.draftTolerance()) return false;
    return moveDirect(ship, oldY, newY);
  }

  private double clamp(Ship ship, double oldY, Body body) {
    double rawY = body.transform().position().y() - ship.origin().y();
    double low = oldY - config.maxFall();
    double high = oldY + config.maxRise();
    double clampedY = Math.min(high, Math.max(low, rawY));
    if (clampedY != rawY) {
      body.setLinearVelocity(new Vector3d());
      body.setTransform(
          new Transform(
              new Vector3d(
                  body.transform().position().x(),
                  ship.origin().y() + clampedY,
                  body.transform().position().z()),
              body.transform().orientation()));
    }
    return clampedY;
  }

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private boolean moveDirect(Ship ship, double oldY, double newY) {
    if (!WaterlineResolver.isPathClear(ship, world, newY, config)) return false;
    try {
      ship.setPose(new ShipPose(newY));
      runtime.move(ship, oldY, newY);
      return true;
    } catch (RuntimeException failure) {
      ship.setPose(new ShipPose(oldY));
      velocities.put(ship.id(), 0.0);
      return false;
    }
  }
}
