package dev.mintychochip.phys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class WorldContactTest {
  @Test
  void supportUnderOneEndPitchesTheFreeEndDown() {
    // rod along Z: unit cubes at local (0,0,2) and (0,0,-2), mass 2, origin (0, 0.4, 0)
    // GravityForce attached. vy starts 0; gravity pulls down.
    // World.isObstacle true only for the voxel under +Z: floor(x)==0 && floor(y)==-1 && floor(z)==2
    BodyImpl rod = rod();
    World world = obstacleWorld();
    PhysicsEngine engine = new PhysicsEngine();
    for (int i = 0; i < 30; i++) {
      engine.step(world, List.of(rod));
      Collisions.resolve(Collisions.detectWorld(world, List.of(rod)));
    }
    Vector3d stern = MassProperties.worldPoint(rod, new Vector3d(0, 0, 2));
    Vector3d bow = MassProperties.worldPoint(rod, new Vector3d(0, 0, -2));
    assertTrue(bow.y() < stern.y() - 0.05, "bow must drop when stern is supported");
    assertTrue(
        Math.abs(rod.angularVelocity().x()) > 1e-4
            || Math.abs(rod.transform().orientation().x()) > 1e-4);
  }

  @Test
  void noObstacleFallsLevel() {
    BodyImpl rod = rod();
    World world = PhysFixtures.world(0.05, new Vector3d(0, -10, 0), PhysFixtures.vacuum());
    PhysicsEngine engine = new PhysicsEngine();
    Vector3d stern0 = MassProperties.worldPoint(rod, new Vector3d(0, 0, 2));
    Vector3d bow0 = MassProperties.worldPoint(rod, new Vector3d(0, 0, -2));
    for (int i = 0; i < 10; i++) {
      engine.step(world, List.of(rod));
      Collisions.resolve(Collisions.detectWorld(world, List.of(rod)));
    }
    Vector3d stern = MassProperties.worldPoint(rod, new Vector3d(0, 0, 2));
    Vector3d bow = MassProperties.worldPoint(rod, new Vector3d(0, 0, -2));
    assertTrue(bow.y() < bow0.y());
    assertTrue(stern.y() < stern0.y());
    assertTrue(Math.abs(bow.y() - stern.y()) < 0.02);
    assertEqualsIdentity(rod.transform().orientation());
  }

  private static BodyImpl rod() {
    Vector3d half = new Vector3d(0.5, 0.5, 0.5);
    return new BodyImpl(
        new Transform(new Vector3d(0, 0.4, 0), new Quaterniond()),
        2,
        List.of(
            PhysFixtures.box(new Vector3d(0, 0, 2), half),
            PhysFixtures.box(new Vector3d(0, 0, -2), half)),
        List.of(new GravityForce()));
  }

  private static World obstacleWorld() {
    return PhysFixtures.world(
        0.05,
        new Vector3d(0, -10, 0),
        PhysFixtures.vacuum(),
        p -> Math.floor(p.x()) == 0 && Math.floor(p.y()) == -1 && Math.floor(p.z()) == 2);
  }

  private static void assertEqualsIdentity(Quaterniondc orientation) {
    assertTrue(Math.abs(orientation.x()) < 1e-9);
    assertTrue(Math.abs(orientation.y()) < 1e-9);
    assertTrue(Math.abs(orientation.z()) < 1e-9);
    assertTrue(Math.abs(orientation.w() - 1.0) < 1e-9);
  }
}
