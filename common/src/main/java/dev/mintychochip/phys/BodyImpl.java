package dev.mintychochip.phys;

import java.util.List;
import java.util.Objects;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniondc;
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

  /** Body-frame mass centroid of AABB colliders, or the origin. */
  private final Vector3d centerOfMassLocal;

  /** Whether the body participates in integration. */
  private boolean active = true;

  /** Immutable collider view. */
  private final List<Collider> colliders;

  /** Immutable force view. */
  private final List<Force> forces;

  /** Body-frame inertia from collider AABBs, or {@code m I} when none. */
  private final Matrix3d bodyInertia;

  /** Inverse of {@link #bodyInertia}. */
  private final Matrix3d bodyInverseInertia;

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
    this.centerOfMassLocal = centerOfMassFromColliders(this.colliders);
    this.bodyInertia = inertiaFromColliders(this.colliders, mass, this.centerOfMassLocal);
    this.bodyInverseInertia = invertInertia(this.bodyInertia, mass);
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
   * Returns world-frame inertia. AABB colliders produce an anisotropic tensor about the center of
   * mass; no colliders fall back to {@code m I}.
   *
   * @return inertia tensor
   */
  public Matrix3dc inertia() {
    return rotateTensor(bodyInertia);
  }

  /**
   * Returns the inverse of {@link #inertia()}.
   *
   * @return inverse inertia tensor
   */
  public Matrix3dc inverseInertia() {
    return rotateTensor(bodyInverseInertia);
  }

  /**
   * Returns the body-frame center of mass.
   *
   * @return mass centroid of colliders, or the origin when there are none
   */
  public Vector3dc centerOfMassLocal() {
    return centerOfMassLocal;
  }

  private Matrix3d rotateTensor(Matrix3dc body) {
    Quaterniondc q = transform.orientation();
    Matrix3d rotation = new Matrix3d().rotation(q);
    Matrix3d world = new Matrix3d();
    rotation.mul(body, world);
    world.mul(rotation.transpose(new Matrix3d()));
    return world;
  }

  private static Vector3d centerOfMassFromColliders(List<Collider> colliders) {
    Vector3d weighted = new Vector3d();
    double colliderMass = 0;
    for (Collider collider : colliders) {
      if (!(collider.shape() instanceof Aabb box)) {
        continue;
      }
      double m = aabbMass(box, collider.material());
      if (m <= 0) {
        continue;
      }
      colliderMass += m;
      weighted.fma(m, colliderCentroid(collider, box));
    }
    if (colliderMass <= 0) {
      return new Vector3d();
    }
    return weighted.div(colliderMass);
  }

  private static Matrix3d inertiaFromColliders(
      List<Collider> colliders, double bodyMass, Vector3dc com) {
    Matrix3d inertia = new Matrix3d().zero();
    double colliderMass = 0;
    for (Collider collider : colliders) {
      if (!(collider.shape() instanceof Aabb box)) {
        continue;
      }
      Vector3dc h = box.halfExtents();
      double m = aabbMass(box, collider.material());
      if (m <= 0) {
        continue;
      }
      colliderMass += m;
      double ixx = m / 3.0 * (h.y() * h.y() + h.z() * h.z());
      double iyy = m / 3.0 * (h.x() * h.x() + h.z() * h.z());
      double izz = m / 3.0 * (h.x() * h.x() + h.y() * h.y());
      Vector3d r = colliderCentroid(collider, box);
      r.sub(com);
      // I += I_cm + m ((r·r) 1 - r rᵀ)
      double r2 = r.lengthSquared();
      inertia.m00 += ixx + m * (r2 - r.x() * r.x());
      inertia.m11 += iyy + m * (r2 - r.y() * r.y());
      inertia.m22 += izz + m * (r2 - r.z() * r.z());
      inertia.m01 += -m * r.x() * r.y();
      inertia.m10 += -m * r.x() * r.y();
      inertia.m02 += -m * r.x() * r.z();
      inertia.m20 += -m * r.x() * r.z();
      inertia.m12 += -m * r.y() * r.z();
      inertia.m21 += -m * r.y() * r.z();
    }
    if (colliderMass <= 0) {
      return new Matrix3d().scaling(bodyMass);
    }
    inertia.scale(bodyMass / colliderMass);
    return inertia;
  }

  private static double aabbMass(Aabb box, Material material) {
    Vector3dc h = box.halfExtents();
    double volume = 8 * h.x() * h.y() * h.z();
    double m = material.density() * volume;
    if (m <= 0 || !Double.isFinite(m)) {
      return 0;
    }
    return m;
  }

  private static Vector3d colliderCentroid(Collider collider, Aabb box) {
    Vector3d r = new Vector3d(box.center());
    collider.localTransform().orientation().transform(r);
    r.add(collider.localTransform().position());
    return r;
  }

  private static Matrix3d invertInertia(Matrix3d inertia, double bodyMass) {
    Matrix3d inverse = new Matrix3d(inertia);
    if (inverse.invert() == null) {
      return new Matrix3d().scaling(1.0 / bodyMass);
    }
    return inverse;
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
