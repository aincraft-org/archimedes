package dev.mintychochip.phys;

import java.util.List;
import java.util.Objects;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Mutable implementation of a rigid body. */
public final class BodyImpl implements Body {
  /** Current world transform. */
  private Transform transform;

  /** Linear velocity. */
  private Vector3d linearVelocity = new Vector3d();

  /** Angular velocity. */
  private Vector3d angularVelocity = new Vector3d();

  /** Constant mass. */
  private final double mass;

  /** Whether the body participates in integration. */
  private boolean active = true;

  /** Immutable collider view. */
  private final List<Collider> colliders;

  /** Immutable force view. */
  private final List<Force> forces;

  /**
   * Creates a rigid body with copied collider and force collections.
   *
   * @param transform initial world transform
   * @param mass positive finite body mass
   * @param colliders colliders defining the body shape
   * @param forces forces evaluated by the physics engine
   * @throws NullPointerException if any argument is {@code null}
   * @throws IllegalArgumentException if {@code mass} is not positive and finite
   */
  public BodyImpl(Transform transform, double mass, List<Collider> colliders, List<Force> forces) {
    this.transform = Objects.requireNonNull(transform);
    if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("mass");
    this.mass = mass;
    this.colliders = List.copyOf(colliders);
    this.forces = List.copyOf(forces);
  }

  /**
   * @return the current world transform
   */
  public Transform transform() {
    return transform;
  }

  /**
   * @param t new non-null world transform
   */
  public void setTransform(Transform t) {
    transform = Objects.requireNonNull(t);
  }

  /**
   * @return the mutable linear velocity vector
   */
  public Vector3dc linearVelocity() {
    return linearVelocity;
  }

  /**
   * @param v new linear velocity, copied into the body's state
   */
  public void setLinearVelocity(Vector3dc v) {
    linearVelocity.set(Objects.requireNonNull(v));
  }

  /**
   * @return the mutable angular velocity vector
   */
  public Vector3dc angularVelocity() {
    return angularVelocity;
  }

  /**
   * @param v new angular velocity, copied into the body's state
   */
  public void setAngularVelocity(Vector3dc v) {
    angularVelocity.set(Objects.requireNonNull(v));
  }

  /**
   * @return the constant positive mass
   */
  public double mass() {
    return mass;
  }

  /**
   * @return the reciprocal of {@link #mass()}
   */
  public double inverseMass() {
    return 1.0 / mass;
  }

  /**
   * Returns the isotropic inertia approximation used by this implementation.
   *
   * @return a diagonal matrix with the mass on each diagonal
   */
  public Matrix3dc inertia() {
    double i = mass;
    return new Matrix3d().set(i, 0, 0, 0, i, 0, 0, 0, i);
  }

  /**
   * Returns the inverse of the isotropic inertia approximation.
   *
   * @return a diagonal matrix with the inverse mass on each diagonal
   */
  public Matrix3dc inverseInertia() {
    double i = 1.0 / mass;
    return new Matrix3d().set(i, 0, 0, 0, i, 0, 0, 0, i);
  }

  /**
   * @return an immutable snapshot list of the body's colliders
   */
  public List<Collider> colliders() {
    return colliders;
  }

  /**
   * @return an immutable snapshot list of forces evaluated for this body
   */
  public List<Force> forces() {
    return forces;
  }

  /**
   * @return whether this body participates in physics integration
   */
  public boolean active() {
    return active;
  }

  /**
   * @param activeValue whether this body should participate in integration
   */
  public void setActive(boolean activeValue) {
    this.active = activeValue;
  }
}
