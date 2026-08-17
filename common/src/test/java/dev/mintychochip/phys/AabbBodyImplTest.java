package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
  void bodyStoresState() {
    Transform t = new Transform(new Vector3d(), new Quaterniond());
    BodyImpl body = new BodyImpl(t, 2, List.of(), List.of());
    body.setLinearVelocity(new Vector3d(1, 0, 0));
    assertEquals(0.5, body.inverseMass(), 1e-9);
    assertEquals(new Vector3d(1, 0, 0), body.linearVelocity());
  }
}
