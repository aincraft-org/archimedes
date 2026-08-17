package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.GravityForce;
import dev.mintychochip.phys.Physics;
import dev.mintychochip.phys.QuadraticDragForce;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;

/**
 * Default ship physics facade that steps attached forces and commits runtime movement.
 *
 * <p>Movement is path-checked and rolled back when the runtime cannot apply it. Linear velocity is
 * retained per ship between ticks and cleared when movement is explicitly reset.
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

  /** Air density used by sails and air drag. */
  private final DensityField air;

  /** Wind sampled by structure sails. */
  private final FlowField wind;

  /** Per-ship retained linear velocity. */
  private final Map<UUID, Vector3d> velocities = new HashMap<>();

  /**
   * Creates a ship physics facade with still air (no sail drive).
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
    this(
        physics,
        world,
        config,
        resolver,
        runtime,
        riderCount,
        DensityField.uniform(1.2),
        FlowField.still());
  }

  /**
   * Creates a ship physics facade with an explicit atmosphere and wind.
   *
   * @param physics generic physics integrator
   * @param world physics world
   * @param config ship configuration
   * @param resolver block material resolver
   * @param runtime runtime movement coordinator
   * @param riderCount rider count provider
   * @param air air density for sails
   * @param wind flow field for sails
   */
  public ShipPhysicsImpl(
      Physics physics,
      World world,
      ShipConfig config,
      MaterialKeyResolver resolver,
      ShipRuntime runtime,
      RiderCount riderCount,
      DensityField air,
      FlowField wind) {
    this.physics = physics;
    this.world = world;
    this.config = config;
    this.resolver = resolver;
    this.runtime = runtime;
    this.riderCount = riderCount;
    this.air = air;
    this.wind = wind;
  }

  /**
   * Advances one physics tick, including sails when cloth is present.
   *
   * @param ship ship to update
   * @return whether the ship moved
   */
  @Override
  public boolean tick(Ship ship) {
    if (!ship.buoyancyEnabled()) return false;
    return integrate(ship, 1, true);
  }

  /**
   * Steps the engine until vertical motion settles, without sail drive.
   *
   * @param ship ship to raise
   * @return whether the request succeeded
   */
  @Override
  public boolean rise(Ship ship) {
    if (!ship.buoyancyEnabled()) return true;
    Body probe = body(ship, false);
    if (WaterlineResolver.submergedVolume(probe, world) == 0) return false;
    velocities.put(ship.id(), new Vector3d());
    return integrate(ship, 80, false);
  }

  /**
   * Moves directly downward by the requested number of blocks, clamped to the fall limit.
   *
   * @param ship ship to sink
   * @param blocks positive downward distance
   * @return whether the path was clear and movement succeeded
   */
  @Override
  public boolean sink(Ship ship, int blocks) {
    if (!ship.buoyancyEnabled() || blocks <= 0) return false;
    ShipPose old = ship.pose();
    double targetY = Math.max(-config.maxFall(), old.y() - blocks);
    return moveDirect(ship, old, new ShipPose(old.x(), targetY, old.z()));
  }

  /**
   * Removes the ship's retained velocity.
   *
   * @param ship ship whose transient physics state is removed
   */
  @Override
  public void clear(Ship ship) {
    velocities.remove(ship.id());
  }

  private Body body(Ship ship, boolean withSails) {
    List<Force> forces = new ArrayList<>();
    forces.add(new GravityForce());
    forces.add(new ShipBuoyancyForce());
    if (withSails) {
      forces.add(new QuadraticDragForce(0.05));
      forces.addAll(ShipSails.forces(ship, resolver, clothKeys(ship), air, wind));
    }
    return ShipBody.from(
        ship, resolver, config, riderCount.count(ship), forces.toArray(Force[]::new));
  }

  private Set<String> clothKeys(Ship ship) {
    Set<String> keys = new HashSet<>();
    for (ShipBlock block : ship.blocks()) {
      String key = resolver.key(block);
      if (isCloth(key)) {
        keys.add(key);
      }
    }
    return keys;
  }

  private static boolean isCloth(String key) {
    return key.endsWith("_wool") || key.endsWith("_banner") || key.endsWith("_wall_banner");
  }

  /**
   * Steps physics, clamps vertical travel, damps retained velocity, and commits movement.
   *
   * @param ship ship being simulated
   * @param steps number of physics steps to perform
   * @param withSails whether to attach sails and air drag
   * @return whether the ship moved
   */
  private boolean integrate(Ship ship, int steps, boolean withSails) {
    ShipPose old = ship.pose();
    Body body = body(ship, withSails);
    body.setLinearVelocity(new Vector3d(velocities.getOrDefault(ship.id(), new Vector3d())));
    double newY = old.y();
    for (int i = 0; i < steps; i++) {
      physics.step(world, List.of(body));
      newY = clamp(ship, old.y(), body);
      if (!withSails && Math.abs(body.linearVelocity().y()) < config.draftTolerance()) {
        break;
      }
    }
    velocities.put(ship.id(), new Vector3d(body.linearVelocity()).mul(config.damping()));
    ShipPose next =
        new ShipPose(
            body.transform().position().x() - ship.origin().x(),
            newY,
            body.transform().position().z() - ship.origin().z());
    if (Math.abs(next.x() - old.x()) < config.draftTolerance()
        && Math.abs(next.y() - old.y()) < config.draftTolerance()
        && Math.abs(next.z() - old.z()) < config.draftTolerance()) {
      return false;
    }
    return moveDirect(ship, old, next);
  }

  /**
   * Clamps a simulated absolute height to the configured rise/fall range.
   *
   * @param ship ship whose origin defines the relative height
   * @param oldY previous relative pose height
   * @param body simulated body whose position is being clamped
   * @return the clamped relative pose height
   */
  private double clamp(Ship ship, double oldY, Body body) {
    double rawY = body.transform().position().y() - ship.origin().y();
    double low = oldY - config.maxFall();
    double high = oldY + config.maxRise();
    double clampedY = Math.min(high, Math.max(low, rawY));
    if (clampedY != rawY) {
      Vector3d velocity = new Vector3d(body.linearVelocity());
      velocity.y = 0;
      body.setLinearVelocity(velocity);
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

  /**
   * Commits a path-checked pose and rolls back pose and velocity on runtime failure.
   *
   * @param ship ship being moved
   * @param oldPose previous pose
   * @param newPose target pose
   * @return whether the runtime move succeeded
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private boolean moveDirect(Ship ship, ShipPose oldPose, ShipPose newPose) {
    if (!WaterlineResolver.isPathClear(ship, world, newPose, config)) return false;
    try {
      ship.setPose(newPose);
      runtime.move(ship, oldPose.y(), newPose.y());
      return true;
    } catch (RuntimeException failure) {
      ship.setPose(oldPose);
      velocities.put(ship.id(), new Vector3d());
      return false;
    }
  }
}
