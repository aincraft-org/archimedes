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
  void singleOffsetColliderInertiaMatchesCubeAboutItsOwnCenter() {
    Aabb box = new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    Collider collider =
        new ColliderImpl(
            box, new Material(1), new Transform(new Vector3d(0, 4, 0), new Quaterniond()));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 1, List.of(collider), List.of());
    // Ixx = m/3 (hy^2+hz^2) = 1/3 * (0.25+0.25) = 1/6
    assertEquals(1.0 / 6.0, body.inertia().m00(), 1e-9);
    assertEquals(body.inertia().m00(), body.inertia().m11(), 1e-9);
    assertEquals(body.inertia().m00(), body.inertia().m22(), 1e-9);
  }

  @Test
  void twoSeparatedCubesHaveLargerInertiaThanOneCube() {
    Aabb box = new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    Collider a =
        new ColliderImpl(
            box, new Material(1), new Transform(new Vector3d(0, 0, 2), new Quaterniond()));
    Collider b =
        new ColliderImpl(
            box, new Material(1), new Transform(new Vector3d(0, 0, -2), new Quaterniond()));
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(a, b), List.of());
    assertEquals(25.0 / 3.0, body.inertia().m00(), 1e-9);
    assertEquals(25.0 / 3.0, body.inertia().m11(), 1e-9);
    assertEquals(1.0 / 3.0, body.inertia().m22(), 1e-9);
  }

  @Test
  void asymmetricCubesHaveInertiaAboutCentroidNotOrigin() {
    Aabb box = new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5));
    Collider heavy =
        new ColliderImpl(
            box, new Material(3), new Transform(new Vector3d(2, 0, 0), new Quaterniond()));
    Collider light =
        new ColliderImpl(
            box, new Material(1), new Transform(new Vector3d(-2, 0, 0), new Quaterniond()));
    BodyImpl body =
        new BodyImpl(
            new Transform(new Vector3d(), new Quaterniond()), 4, List.of(heavy, light), List.of());
    assertEquals(2.0 / 3.0, body.inertia().m00(), 1e-9);
    assertEquals(38.0 / 3.0, body.inertia().m11(), 1e-9);
    assertEquals(38.0 / 3.0, body.inertia().m22(), 1e-9);
  }
}
