package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MathRecordsTest {
  @Test void recordsRetainComponents() {
    assertEquals(new Vector3(1, 2, 3), new Vector3(1, 2, 3));
    assertEquals(new Quaternion(0, 0, 0, 1), new Quaternion(0, 0, 0, 1));
    assertEquals(new Transform(new Vector3(1, 2, 3), new Quaternion(0, 0, 0, 1)),
        new Transform(new Vector3(1, 2, 3), new Quaternion(0, 0, 0, 1)));
    assertEquals(9, new Matrix3x3(9, 0, 0, 0, 9, 0, 0, 0, 9).m00());
  }

  @Test void rejectsInvalidNumbersAndQuaternionNorm() {
    assertThrows(IllegalArgumentException.class, () -> new Vector3(Double.NaN, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new Quaternion(0, 0, 0, 2));
  }

  @Test void quaternionRotatesVector() {
    Quaternion q = new Quaternion(0, 0, Math.sin(Math.PI / 4), Math.cos(Math.PI / 4));
    Vector3 actual = q.rotate(new Vector3(0, 1, 0));
    assertEquals(-1.0, actual.x(), 1e-9);
    assertEquals(0.0, actual.y(), 1e-9);
    assertEquals(0.0, actual.z(), 1e-9);
  }
}
