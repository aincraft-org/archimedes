package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.World;
import java.util.Objects;
import org.joml.Vector3d;

/** Aerostatic lift from envelope volume only: {@code F = −ρ V g}. */
public final class EnvelopeBuoyancyForce implements Force {
  /** Envelope volume in cubic meters. Collider volumes are ignored. */
  private final double volume;

  /** Air density sampled at the body position. */
  private final DensityField air;

  /**
   * Creates aerostatic lift from a prescribed envelope volume.
   *
   * @param volume finite non-negative envelope volume
   * @param air density sampler at the body position
   * @throws IllegalArgumentException if {@code volume} is negative or non-finite
   * @throws NullPointerException if {@code air} is {@code null}
   */
  public EnvelopeBuoyancyForce(double volume, DensityField air) {
    if (!Double.isFinite(volume) || volume < 0) {
      throw new IllegalArgumentException("volume must be finite and non-negative");
    }
    this.volume = volume;
    this.air = Objects.requireNonNull(air);
  }

  /**
   * Applies {@code F = −ρ V g} using envelope volume only, not hull colliders.
   *
   * @param body body whose position is sampled for air density
   * @param world world supplying gravity
   * @return lift opposing gravity and zero torque
   * @throws NullPointerException if either argument is {@code null}
   */
  @Override
  public Result apply(Body body, World world) {
    Objects.requireNonNull(body);
    Objects.requireNonNull(world);
    double rho = air.density(body.transform().position());
    double mass = rho * volume;
    return new Result(new Vector3d(world.gravity()).mul(-mass), new Vector3d());
  }
}
