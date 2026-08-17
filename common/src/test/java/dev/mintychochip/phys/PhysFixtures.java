package dev.mintychochip.phys;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

final class PhysFixtures {
  private PhysFixtures() {}

  static World world(double dt, Vector3dc gravity, FluidField fluids) {
    Vector3d g = new Vector3d(gravity);
    return new World() {
      public Vector3dc gravity() {
        return g;
      }

      public FluidField fluidField() {
        return fluids;
      }

      public double timeStep() {
        return dt;
      }
    };
  }

  static FluidField vacuum() {
    return new FluidField() {
      public boolean isFluid(Vector3dc point) {
        return false;
      }

      public double density(Vector3dc point) {
        return 0;
      }
    };
  }

  static FluidField liquidBelow(double surfaceY, double density) {
    return new FluidField() {
      public boolean isFluid(Vector3dc point) {
        return point.y() < surfaceY;
      }

      public double density(Vector3dc point) {
        return density;
      }
    };
  }

  static Collider box(Vector3dc center, Vector3dc halfExtents) {
    return new ColliderImpl(
        new Aabb(center, halfExtents),
        new Material(1),
        new Transform(new Vector3d(), new Quaterniond()));
  }
}
