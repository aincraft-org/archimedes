package dev.mintychochip.archimedes.phys;

import dev.mintychochip.phys.Aabb;
import dev.mintychochip.phys.AngularDragForce;
import dev.mintychochip.phys.BodyImpl;
import dev.mintychochip.phys.ColliderImpl;
import dev.mintychochip.phys.GravityForce;
import dev.mintychochip.phys.Material;
import dev.mintychochip.phys.Physics;
import dev.mintychochip.phys.QuadraticDragForce;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.World;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A torn cloth cell that falls and tumbles independently of the vehicle.
 *
 * <p>Stepped as its own rigid body with gravity, lumped drag, and angular drag.
 */
public final class ClothDebris {
  /** Debris identity used by the runtime ragdoll. */
  private final UUID id;

  /** Captured block data drawn on the ragdoll. */
  private final String appearance;

  /** World-space pose. */
  private Transform transform;

  /** Linear velocity. */
  private final Vector3d linear = new Vector3d();

  /** Angular velocity. */
  private final Vector3d angular = new Vector3d();

  /**
   * @param appearance captured block data
   * @param position world-space visual origin
   * @param linearVelocity initial linear velocity
   * @param angularVelocity initial spin
   */
  public ClothDebris(
      String appearance, Vector3dc position, Vector3dc linearVelocity, Vector3dc angularVelocity) {
    this.id = UUID.randomUUID();
    this.appearance = Objects.requireNonNull(appearance);
    this.transform =
        new Transform(new Vector3d(Objects.requireNonNull(position)), new Quaterniond());
    this.linear.set(Objects.requireNonNull(linearVelocity));
    this.angular.set(Objects.requireNonNull(angularVelocity));
  }

  /**
   * Integrates one debris step.
   *
   * @param world gravity and timestep
   * @param physics integrator
   */
  public void step(World world, Physics physics) {
    BodyImpl body =
        new BodyImpl(
            transform,
            1,
            List.of(
                new ColliderImpl(
                    new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5)),
                    new Material(1),
                    new Transform(new Vector3d(), new Quaterniond()))),
            List.of(new GravityForce(), new QuadraticDragForce(0.4), new AngularDragForce(0.8)));
    body.setLinearVelocity(linear);
    body.setAngularVelocity(angular);
    physics.step(world, List.of(body));
    transform = body.transform();
    linear.set(body.linearVelocity());
    angular.set(body.angularVelocity());
  }

  /**
   * @return debris identity
   */
  public UUID id() {
    return id;
  }

  /**
   * @return captured block data
   */
  public String appearance() {
    return appearance;
  }

  /**
   * @return world position
   */
  public Vector3dc position() {
    return transform.position();
  }

  /**
   * @return orientation
   */
  public Quaterniond orientation() {
    return new Quaterniond(transform.orientation());
  }

  /**
   * @return spin
   */
  public Vector3dc angularVelocity() {
    return angular;
  }
}
