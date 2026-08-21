package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class MassPropertiesTest {
  @Test
  void centerOfMassIsDensityWeightedColliderCentroid() {
    BodyImpl body = densityWeightedBody();
    assertEquals(1.0, body.centerOfMassLocal().x(), 1e-9);
    assertEquals(0.0, body.centerOfMassLocal().y(), 1e-9);
    assertEquals(0.0, body.centerOfMassLocal().z(), 1e-9);
  }

  @Test
  void worldRadiusIsOffsetFromCenterOfMass() {
    BodyImpl body = densityWeightedBody();
    Vector3d radius = MassProperties.radiusAboutCom(body, new Vector3d(2, 0, 0));
    assertEquals(1.0, radius.x(), 1e-9);
    assertEquals(0.0, radius.y(), 1e-9);
    assertEquals(0.0, radius.z(), 1e-9);
  }

  @Test
  void noCollidersCenterOfMassIsOrigin() {
    BodyImpl body =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 4, List.of(), List.of());
    assertEquals(0.0, body.centerOfMassLocal().length(), 1e-12);
  }

  private static BodyImpl densityWeightedBody() {
    Collider heavy =
        new ColliderImpl(
            new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5)),
            new Material(3),
            new Transform(new Vector3d(2, 0, 0), new Quaterniond()));
    Collider light =
        new ColliderImpl(
            new Aabb(new Vector3d(), new Vector3d(0.5, 0.5, 0.5)),
            new Material(1),
            new Transform(new Vector3d(-2, 0, 0), new Quaterniond()));
    return new BodyImpl(
        new Transform(new Vector3d(), new Quaterniond()), 4, List.of(heavy, light), List.of());
  }
}
