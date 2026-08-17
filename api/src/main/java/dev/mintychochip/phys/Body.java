package dev.mintychochip.phys;

import java.util.List;

public interface Body {
  Transform transform();

  void setTransform(Transform t);

  Vector3 linearVelocity();

  void setLinearVelocity(Vector3 v);

  Vector3 angularVelocity();

  void setAngularVelocity(Vector3 v);

  double mass();

  double inverseMass();

  Matrix3x3 inertia();

  Matrix3x3 inverseInertia();

  List<Collider> colliders();

  List<Force> forces();

  boolean active();

  void setActive(boolean active);
}
