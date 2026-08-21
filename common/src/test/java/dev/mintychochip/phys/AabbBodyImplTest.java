package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class AabbBodyImplTest {
  @Test
  void aabbIsShapeAndBounds() {
    Aabb box = new Aabb(new Vector3d(0.5, 0.5, 0.5), new Vector3d(0.5, 0.5, 0.5));
    assertEquals(1, box.volume(), 1e-9);
    assertTrue(box.contains(new Vector3d(0.5, 0.5, 0.5)));
    Bounds world = box.bounds(new Transform(new Vector3d(1, 2, 3), new Quaterniond()));
    assertEquals(new Vector3d(1.0, 2.0, 3.0), world.min());
    assertEquals(new Vector3d(2.0, 3.0, 4.0), world.max());
  }

  @Test
  void composeRotatesLocalOffsetThenAddsParentTranslation() {
    Transform parent =
        new Transform(new Vector3d(10, 0, 0), new Quaterniond().rotateY(Math.PI / 2));
    Transform local = new Transform(new Vector3d(1, 0, 0), new Quaterniond());
    Transform world = parent.compose(local);
    assertEquals(10.0, world.position().x(), 1e-9);
    assertEquals(0.0, world.position().y(), 1e-9);
    assertEquals(-1.0, world.position().z(), 1e-9);
  }

  @Test
  void composeMultipliesOrientationsParentThenLocal() {
    Transform parent = new Transform(new Vector3d(), new Quaterniond().rotateY(Math.PI / 2));
    Transform local = new Transform(new Vector3d(), new Quaterniond().rotateX(Math.PI / 2));
    Transform world = parent.compose(local);
    Vector3d v = world.orientation().transform(new Vector3d(0, 1, 0));
    assertEquals(1.0, v.x(), 1e-9);
    assertEquals(0.0, v.y(), 1e-9);
    assertEquals(0.0, v.z(), 1e-9);
  }

  @Test
  void boundsGrowsToWorldAabbOfRotatedBox() {
    Aabb box = new Aabb(new Vector3d(), new Vector3d(1, 0.5, 0.5));
    Transform yaw = new Transform(new Vector3d(), new Quaterniond().rotateY(Math.PI / 2));
    Bounds world = box.bounds(yaw);
    assertEquals(-0.5, world.min().x(), 1e-9);
    assertEquals(-0.5, world.min().y(), 1e-9);
    assertEquals(-1.0, world.min().z(), 1e-9);
    assertEquals(0.5, world.max().x(), 1e-9);
    assertEquals(0.5, world.max().y(), 1e-9);
    assertEquals(1.0, world.max().z(), 1e-9);
  }

  @Test
  void boundsUsesRowsOfRotationMatrixNotColumns() {
    Quaterniond q = new Quaterniond().rotateX(Math.PI / 4).rotateY(Math.PI / 4);
    Aabb box = new Aabb(new Vector3d(), new Vector3d(1, 0.5, 0.5));
    Bounds world = box.bounds(new Transform(new Vector3d(), q));
    Matrix3d r = new Matrix3d().rotation(q);
    Vector3d expectedHalf =
        new Vector3d(
            Math.abs(r.m00()) * 1 + Math.abs(r.m10()) * 0.5 + Math.abs(r.m20()) * 0.5,
            Math.abs(r.m01()) * 1 + Math.abs(r.m11()) * 0.5 + Math.abs(r.m21()) * 0.5,
            Math.abs(r.m02()) * 1 + Math.abs(r.m12()) * 0.5 + Math.abs(r.m22()) * 0.5);
    assertEquals(expectedHalf.x(), world.max().x(), 1e-9);
    assertEquals(expectedHalf.y(), world.max().y(), 1e-9);
    assertEquals(expectedHalf.z(), world.max().z(), 1e-9);
  }

  @Test
  void boundsRotatesNonOriginCenterThenTranslates() {
    Aabb box = new Aabb(new Vector3d(1, 0, 0), new Vector3d(0.5, 0.5, 0.5));
    Transform pose = new Transform(new Vector3d(10, 0, 0), new Quaterniond().rotateY(Math.PI / 2));
    Bounds world = box.bounds(pose);
    assertEquals(9.5, world.min().x(), 1e-9);
    assertEquals(-0.5, world.min().y(), 1e-9);
    assertEquals(-1.5, world.min().z(), 1e-9);
    assertEquals(10.5, world.max().x(), 1e-9);
    assertEquals(0.5, world.max().y(), 1e-9);
    assertEquals(-0.5, world.max().z(), 1e-9);
  }

  @Test
  void bodyStoresState() {
    Transform t = new Transform(new Vector3d(), new Quaterniond());
    BodyImpl body = new BodyImpl(t, 2, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(1, 0, 0));
    assertEquals(0.5, body.inverseMass(), 1e-9);
    assertEquals(new Vector3d(1, 0, 0), body.linearVelocity());
  }
}
