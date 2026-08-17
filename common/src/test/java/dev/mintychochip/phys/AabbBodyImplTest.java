package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AabbBodyImplTest {
  @Test void aabbIsShapeAndBounds() {
    Aabb box = new Aabb(new Vector3(0.5, 0.5, 0.5), new Vector3(0.5, 0.5, 0.5));
    assertEquals(1, box.volume(), 1e-9);
    assertTrue(box.contains(new Vector3(0.5, 0.5, 0.5)));
    Bounds world = box.bounds(new Transform(new Vector3(1, 2, 3), new Quaternion(0, 0, 0, 1)));
    assertEquals(new Vector3(1.0, 2.0, 3.0), world.min());
    assertEquals(new Vector3(2.0, 3.0, 4.0), world.max());
  }

  @Test void bodyStoresState() {
    Transform t = new Transform(Vector3.ZERO, new Quaternion(0, 0, 0, 1));
    BodyImpl body = new BodyImpl(t, 2, List.of(), List.of());
    body.setLinearVelocity(new Vector3(1, 0, 0));
    assertEquals(0.5, body.inverseMass(), 1e-9);
    assertEquals(new Vector3(1, 0, 0), body.linearVelocity());
  }
}
