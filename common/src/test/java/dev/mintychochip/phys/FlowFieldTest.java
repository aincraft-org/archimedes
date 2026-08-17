package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class FlowFieldTest {
  @Test
  void stillIsZeroEverywhere() {
    FlowField still = FlowField.still();

    assertEquals(0.0, still.velocity(new Vector3d()).length(), 0.0);
    assertEquals(0.0, still.velocity(new Vector3d(10, -4, 3)).length(), 0.0);
  }

  @Test
  void uniformIsTheSameVelocityAtEveryPoint() {
    FlowField wind = FlowField.uniform(new Vector3d(3, 0, -1));

    assertEquals(new Vector3d(3, 0, -1), wind.velocity(new Vector3d()));
    assertEquals(new Vector3d(3, 0, -1), wind.velocity(new Vector3d(100, 50, -20)));
  }

  @Test
  void boxIsWindInsideAndStillOutside() {
    FlowField canyon =
        FlowField.box(new Vector3d(-2, 0, -2), new Vector3d(2, 8, 2), new Vector3d(0, 0, 5));

    assertEquals(new Vector3d(0, 0, 5), canyon.velocity(new Vector3d(0, 4, 0)));
    assertEquals(new Vector3d(0, 0, 5), canyon.velocity(new Vector3d(-2, 0, 2)));
    assertEquals(0.0, canyon.velocity(new Vector3d(3, 4, 0)).length(), 0.0);
    assertEquals(0.0, canyon.velocity(new Vector3d(0, -1, 0)).length(), 0.0);
  }

  @Test
  void composeSumsIndependentFieldsAtAPoint() {
    FlowField trade = FlowField.uniform(new Vector3d(4, 0, 0));
    FlowField gust = FlowField.uniform(new Vector3d(0, 0, 2));
    FlowField both = FlowField.compose(trade, gust);

    assertEquals(new Vector3d(4, 0, 2), both.velocity(new Vector3d(1, 1, 1)));
    assertEquals(new Vector3d(4, 0, 0), trade.velocity(new Vector3d(1, 1, 1)));
    assertEquals(new Vector3d(0, 0, 2), gust.velocity(new Vector3d(1, 1, 1)));
  }

  @Test
  void composeOverlapsSpatialFieldsOnlyWhereTheyOverlap() {
    FlowField west =
        FlowField.box(new Vector3d(0, 0, 0), new Vector3d(2, 2, 2), new Vector3d(3, 0, 0));
    FlowField east =
        FlowField.box(new Vector3d(1, 0, 0), new Vector3d(3, 2, 2), new Vector3d(0, 0, 4));
    FlowField both = FlowField.compose(west, east);

    assertEquals(new Vector3d(3, 0, 0), both.velocity(new Vector3d(0.5, 1, 1)));
    assertEquals(new Vector3d(0, 0, 4), both.velocity(new Vector3d(2.5, 1, 1)));
    assertEquals(new Vector3d(3, 0, 4), both.velocity(new Vector3d(1.5, 1, 1)));
    assertEquals(0.0, both.velocity(new Vector3d(4, 1, 1)).length(), 0.0);
  }

  @Test
  void emptyComposeIsStill() {
    assertEquals(0.0, FlowField.compose().velocity(new Vector3d(2, 2, 2)).length(), 0.0);
  }

  @Test
  void returnedVelocityIsADefensiveCopy() {
    FlowField wind = FlowField.uniform(new Vector3d(1, 0, 0));
    ((Vector3d) wind.velocity(new Vector3d())).set(9, 9, 9);

    assertEquals(new Vector3d(1, 0, 0), wind.velocity(new Vector3d()));
  }

  @Test
  void rejectsNonFiniteUniformAndInvertedBox() {
    assertThrows(
        IllegalArgumentException.class, () -> FlowField.uniform(new Vector3d(Double.NaN, 0, 0)));
    assertThrows(
        IllegalArgumentException.class,
        () -> FlowField.box(new Vector3d(1, 0, 0), new Vector3d(0, 1, 1), new Vector3d(1, 0, 0)));
  }
}
