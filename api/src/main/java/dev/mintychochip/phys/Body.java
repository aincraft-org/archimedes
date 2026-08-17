package dev.mintychochip.phys;

import java.util.List;
import org.joml.Matrix3dc;
import org.joml.Vector3dc;

public interface Body {
  Transform transform();

  void setTransform(Transform t);

  Vector3dc linearVelocity();

  void setLinearVelocity(Vector3dc v);

  Vector3dc angularVelocity();

  void setAngularVelocity(Vector3dc v);

  double mass();

  double inverseMass();

  Matrix3dc inertia();

  Matrix3dc inverseInertia();

  List<Collider> colliders();

  List<Force> forces();

  boolean active();

  void setActive(boolean active);
}
