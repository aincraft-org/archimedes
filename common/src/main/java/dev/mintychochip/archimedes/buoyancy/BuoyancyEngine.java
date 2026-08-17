package dev.mintychochip.archimedes.buoyancy;

import dev.mintychochip.archimedes.model.Ship;

/** Vertical rigid-body integration: buoyancy force vs. weight. */
public final class BuoyancyEngine {
  /** Gravity constant. */
  private final double gravity;

  /** Water density. */
  private final double waterDensity;

  /** Block density (uniform). */
  private final double blockDensity;

  /** Velocity damping factor per tick. */
  private final double damping;

  /** Fixed timestep. */
  private final double timestep;

  /**
   * Creates the engine.
   *
   * @param gravity the gravity constant
   * @param waterDensity the water density
   * @param blockDensity the block density
   * @param damping the velocity damping
   * @param timestep the fixed timestep
   */
  public BuoyancyEngine(
      double gravity, double waterDensity, double blockDensity, double damping, double timestep) {
    this.gravity = gravity;
    this.waterDensity = waterDensity;
    this.blockDensity = blockDensity;
    this.damping = damping;
    this.timestep = timestep;
  }

  /**
   * Integrates one tick for the ship.
   *
   * @param ship the ship
   * @param velocity the current vertical velocity
   * @param surface the world surface
   * @return the new pose y and velocity
   */
  public Step step(Ship ship, double velocity, BuoyancySurface surface) {
    int mass = ship.blockCount();
    int submerged = BuoyancyResolver.submergedVolume(ship, surface);
    double weight = mass * blockDensity * gravity;
    double buoyancy = waterDensity * gravity * submerged;
    double netForce = buoyancy - weight;
    double acceleration = mass == 0 ? 0 : netForce / mass;
    double newVelocity = (velocity + acceleration * timestep) * damping;
    double newY = ship.pose().y() + newVelocity * timestep;
    return new Step(newY, newVelocity);
  }

  /**
   * One integration result.
   *
   * @param y the new pose y
   * @param velocity the new vertical velocity
   */
  public record Step(double y, double velocity) {}
}
