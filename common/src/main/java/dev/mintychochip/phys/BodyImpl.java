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

  public BodyImpl(Transform transform, double mass, List<Collider> colliders, List<Force> forces) {
    this.transform = Objects.requireNonNull(transform);
    if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("mass");
    this.mass = mass;
    this.colliders = List.copyOf(colliders);
    this.forces = List.copyOf(forces);
  }

  public Transform transform() {
    return transform;
  }

  public void setTransform(Transform t) {
    transform = Objects.requireNonNull(t);
  }

  public Vector3dc linearVelocity() {
    return linearVelocity;
  }

  public void setLinearVelocity(Vector3dc v) {
    linearVelocity.set(Objects.requireNonNull(v));
  }

  public Vector3dc angularVelocity() {
    return angularVelocity;
  }

  public void setAngularVelocity(Vector3dc v) {
    angularVelocity.set(Objects.requireNonNull(v));
  }

  public double mass() {
    return mass;
  }

  public double inverseMass() {
    return 1.0 / mass;
  }

  public Matrix3dc inertia() {
    double i = mass;
    return new Matrix3d().set(i, 0, 0, 0, i, 0, 0, 0, i);
  }

  public Matrix3dc inverseInertia() {
    double i = 1.0 / mass;
    return new Matrix3d().set(i, 0, 0, 0, i, 0, 0, 0, i);
  }

  public List<Collider> colliders() {
    return colliders;
  }

  public List<Force> forces() {
    return forces;
  }

  public boolean active() {
    return active;
  }

  public void setActive(boolean activeValue) {
    this.active = activeValue;
  }
}
