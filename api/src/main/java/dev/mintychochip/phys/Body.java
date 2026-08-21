package dev.mintychochip.phys;

import java.util.List;
import org.joml.Matrix3dc;
import org.joml.Vector3dc;

/** A generic rigid body whose state is integrated by {@link Physics}. */
public interface Body {
  /**
   * Returns the body's world-space transform.
   *
   * @return world-space transform
   */
  Transform transform();

  /**
   * Replaces the body's world-space transform.
   *
   * @param t new world-space transform
   */
  void setTransform(Transform t);

  /**
   * Returns the body's world-space linear velocity.
   *
   * @return linear velocity
   */
  Vector3dc linearVelocity();

  /**
   * Sets the body's world-space linear velocity.
   *
   * @param v new linear velocity
   */
  void setLinearVelocity(Vector3dc v);

  /**
   * Returns the body's angular velocity in world coordinates.
   *
   * @return angular velocity
   */
  Vector3dc angularVelocity();

  /**
   * Sets the body's angular velocity in world coordinates.
   *
   * @param v new angular velocity
   */
  void setAngularVelocity(Vector3dc v);

  /**
   * Returns the body's mass.
   *
   * @return mass
   */
  double mass();

  /**
   * Returns the reciprocal of {@link #mass()}.
   *
   * @return inverse mass
   */
  double inverseMass();

  /**
   * Returns the body's inertia tensor.
   *
   * @return inertia tensor
   */
  Matrix3dc inertia();

  /**
   * Returns the inverse inertia tensor.
   *
   * @return inverse inertia tensor
   */
  Matrix3dc inverseInertia();

  /**
   * Returns the body-frame center of mass.
   *
   * @return mass centroid of colliders, or the origin when there are none
   */
  Vector3dc centerOfMassLocal();

  /**
   * Returns the body's fixed, unmodifiable collider collection.
   *
   * @return colliders
   */
  List<Collider> colliders();

  /**
   * Returns the body's fixed, unmodifiable force collection.
   *
   * @return forces
   */
  List<Force> forces();

  /**
   * Returns whether this body participates in simulation steps.
   *
   * @return whether the body is active
   */
  boolean active();

  /**
   * Enables or disables participation in simulation steps.
   *
   * @param active whether the body should participate
   */
  void setActive(boolean active);
}
