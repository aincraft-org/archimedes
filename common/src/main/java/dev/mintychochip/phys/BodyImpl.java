package dev.mintychochip.phys;

import java.util.List;
import java.util.Objects;

public final class BodyImpl implements Body {
  private Transform transform;
  private Vector3 linearVelocity = Vector3.ZERO;
  private Vector3 angularVelocity = Vector3.ZERO;
  private final double mass;
  private boolean active = true;
  private final List<Collider> colliders;
  private final List<Force> forces;

  public BodyImpl(Transform transform, double mass, List<Collider> colliders, List<Force> forces) {
    this.transform = Objects.requireNonNull(transform);
    if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("mass");
    this.mass = mass;
    this.colliders = List.copyOf(colliders);
    this.forces = List.copyOf(forces);
  }

  public Transform transform() { return transform; }
  public void setTransform(Transform t) { transform = Objects.requireNonNull(t); }
  public Vector3 linearVelocity() { return linearVelocity; }
  public void setLinearVelocity(Vector3 v) { linearVelocity = Objects.requireNonNull(v); }
  public Vector3 angularVelocity() { return angularVelocity; }
  public void setAngularVelocity(Vector3 v) { angularVelocity = Objects.requireNonNull(v); }
  public double mass() { return mass; }
  public double inverseMass() { return 1.0 / mass; }
  public Matrix3x3 inertia() { double i = mass; return new Matrix3x3(i, 0, 0, 0, i, 0, 0, 0, i); }
  public Matrix3x3 inverseInertia() { double i = 1.0 / mass; return new Matrix3x3(i, 0, 0, 0, i, 0, 0, 0, i); }
  public List<Collider> colliders() { return colliders; }
  public List<Force> forces() { return forces; }
  public boolean active() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
