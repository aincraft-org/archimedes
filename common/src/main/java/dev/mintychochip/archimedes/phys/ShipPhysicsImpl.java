package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Physics;
import dev.mintychochip.phys.World;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3d;

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

  @Override
  public boolean tick(Ship ship) {
    if (!ship.buoyancyEnabled()) return false;
    EquilibriumResult equilibrium = computeTarget(ship);
    double oldY = ship.pose().y();
    double targetY =
        equilibrium.equilibrium() ? Math.min(config.maxRise(), oldY + equilibrium.targetY()) : oldY;
    return step(ship, oldY, targetY);
  }

  @Override
  public boolean rise(Ship ship) {
    if (!ship.buoyancyEnabled()) return true;
    EquilibriumResult equilibrium = computeTarget(ship);
    if (!equilibrium.equilibrium()) return false;
    double oldY = ship.pose().y();
    double targetY = Math.min(config.maxRise(), oldY + equilibrium.targetY());
    velocities.put(ship.id(), 0.0);
    return moveDirect(ship, oldY, targetY);
  }

  @Override
  public boolean sink(Ship ship, int blocks) {
    if (!ship.buoyancyEnabled() || blocks <= 0) return false;
    double oldY = ship.pose().y();
    double targetY = Math.max(-config.maxFall(), oldY - blocks);
    return moveDirect(ship, oldY, targetY);
  }

  @Override
  public void clear(Ship ship) {
    velocities.remove(ship.id());
  }

  private EquilibriumResult computeTarget(Ship ship) {
    Body body =
        ShipBody.from(ship, resolver, config, riderCount.count(ship), new ShipBuoyancyForce());
    return new EquilibriumSolver().solve(body, world, config);
  }

  private boolean step(Ship ship, double oldY, double targetY) {
    Body body =
        ShipBody.from(ship, resolver, config, riderCount.count(ship), new ShipBuoyancyForce());
    double velocity = velocities.getOrDefault(ship.id(), 0.0);
    body.setLinearVelocity(new Vector3d(0, velocity, 0));
    physics.step(world, List.of(body));
    double rawY = body.transform().position().y() - ship.origin().y();
    double newY = clampAndDamp(ship, oldY, targetY, rawY, body);
    if (Math.abs(newY - oldY) < config.draftTolerance()) return false;
    return moveDirect(ship, oldY, newY);
  }

  private double clampAndDamp(Ship ship, double oldY, double targetY, double rawY, Body body) {
    double low = Math.max(oldY - config.maxFall(), targetY - config.bobAmplitude());
    double high = Math.min(oldY + config.maxRise(), targetY + config.bobAmplitude());
    double clampedY = rawY;
    if (clampedY < low) {
      clampedY = low;
      body.setLinearVelocity(new Vector3d());
    }
    if (clampedY > high) {
      clampedY = high;
      body.setLinearVelocity(new Vector3d());
    }
    velocities.put(ship.id(), body.linearVelocity().y() * config.damping());
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
