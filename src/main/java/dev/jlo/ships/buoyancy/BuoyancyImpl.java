package dev.jlo.ships.buoyancy;

import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipPose;
import dev.jlo.ships.ship.ShipRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Default buoyancy: rises on assembly, integrates bobbing, applies moves all-or-nothing. */
public final class BuoyancyImpl implements Buoyancy {
  /** Runtime for atomic pose, display, and collision movement. */
  private final ShipRuntime runtime;

  /** Surface used to evaluate water and clearance. */
  private final BuoyancySurface surface;

  /** Force integrator. */
  private final BuoyancyEngine engine;

  /** Maximum rise from build site. */
  private final double maxRise;

  /** Maximum vertical bob amplitude. */
  private final double bobAmplitude;

  /** Per-ship vertical velocity. */
  private final Map<UUID, Double> velocities = new HashMap<>();

  /** Per-ship equilibrium pose y. */
  private final Map<UUID, Double> equilibria = new HashMap<>();

  /**
   * Creates the buoyancy implementation.
   *
   * @param surface the world surface
   * @param engine the force integrator
   * @param runtime the ship runtime
   * @param maxRise the maximum rise from build site
   * @param bobAmplitude the maximum bob amplitude
   */
  public BuoyancyImpl(
      BuoyancySurface surface,
      BuoyancyEngine engine,
      ShipRuntime runtime,
      double maxRise,
      double bobAmplitude) {
    this.surface = surface;
    this.engine = engine;
    this.runtime = runtime;
    this.maxRise = maxRise;
    this.bobAmplitude = bobAmplitude;
  }

  @Override
  public boolean rise(Ship ship) {
    if (!ship.buoyancyEnabled()) {
      return true;
    }
    double target = Math.min(maxRise, BuoyancyResolver.equilibriumY(ship, surface));
    double oldY = ship.pose().y();
    if (!pathClear(ship, oldY, target)) {
      return false;
    }
    velocities.put(ship.id(), 0.0);
    equilibria.put(ship.id(), target);
    return moveTo(ship, oldY, target);
  }

  @Override
  public boolean tick(Ship ship) {
    if (!ship.buoyancyEnabled()) {
      return false;
    }
    double velocity = velocities.getOrDefault(ship.id(), 0.0);
    BuoyancyEngine.Step step = engine.step(ship, velocity, surface);
    double equilibrium = equilibria.getOrDefault(ship.id(), ship.pose().y());
    double lower = equilibrium - bobAmplitude;
    double upper = Math.min(maxRise, equilibrium + bobAmplitude);
    double nextY = step.y();
    double nextVelocity = step.velocity();
    if (nextY < lower) {
      nextY = lower;
      nextVelocity = Math.abs(nextVelocity);
    } else if (nextY > upper) {
      nextY = upper;
      nextVelocity = -Math.abs(nextVelocity);
    }
    if (Math.abs(nextY - ship.pose().y()) < 0.001) {
      velocities.put(ship.id(), nextVelocity);
      return false;
    }
    if (!pathClear(ship, ship.pose().y(), nextY)) {
      velocities.put(ship.id(), 0.0);
      return false;
    }
    velocities.put(ship.id(), nextVelocity);
    return moveTo(ship, ship.pose().y(), nextY);
  }

  @Override
  public boolean sink(Ship ship, int blocks) {
    if (!ship.buoyancyEnabled()) {
      return false;
    }
    double target = ship.pose().y() - blocks;
    if (!pathClear(ship, ship.pose().y(), target)) {
      return false;
    }
    return moveTo(ship, ship.pose().y(), target);
  }

  @Override
  public void clear(Ship ship) {
    velocities.remove(ship.id());
    equilibria.remove(ship.id());
  }

  private boolean moveTo(Ship ship, double oldY, double newY) {
    try {
      ship.setPose(new ShipPose(newY));
      runtime.move(ship, oldY, newY);
      return true;
    } catch (dev.jlo.ships.ship.ShipRuntimeException failure) {
      ship.setPose(new ShipPose(oldY));
      return false;
    }
  }

  private boolean pathClear(Ship ship, double fromY, double toY) {
    int from = (int) Math.floor(Math.min(fromY, toY));
    int to = (int) Math.floor(Math.max(fromY, toY));
    for (int y = from; y <= to; y++) {
      for (var block : ship.blocks()) {
        int ax = ship.origin().x() + block.pos().x();
        int ay = ship.origin().y() + y + block.pos().y();
        int az = ship.origin().z() + block.pos().z();
        if (!surface.isClear(ax, ay, az) && !surface.isWater(ax, ay, az)) {
          return false;
        }
      }
    }
    return true;
  }
}
