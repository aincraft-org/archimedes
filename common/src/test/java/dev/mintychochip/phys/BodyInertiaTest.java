package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class BodyInertiaTest {
  @Test
  void longBoxHasLargerInertiaAboutShortAxes() {
    Aabb rod = new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 5));
    double volume = 8 * 0.5 * 0.5 * 5;
    Collider collider =
        new ColliderImpl(rod, new Material(1), new Transform(new Vector3d(), new Quaterniond()));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), volume, List.of(collider), List.of());

    Matrix3dc inertia = body.inertia();
    assertEquals(inertia.m00(), inertia.m11(), 1e-9);
    assertTrue(inertia.m00() > inertia.m22());
    assertEquals(0.0, inertia.m01(), 1e-12);
    assertEquals(0.0, inertia.m02(), 1e-12);
    assertEquals(0.0, inertia.m12(), 1e-12);
  }

  @Test
  void offsetColliderAddsParallelAxisMass() {
    Aabb box = new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    Collider collider =
        new ColliderImpl(
            box, new Material(1), new Transform(new Vector3d(0, 4, 0), new Quaterniond()));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 1, List.of(collider), List.of());

    Matrix3dc inertia = body.inertia();
    assertTrue(inertia.m00() > inertia.m11());
    assertEquals(inertia.m00(), inertia.m22(), 1e-9);
  }
}
