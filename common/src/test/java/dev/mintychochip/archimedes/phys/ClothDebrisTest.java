package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.World;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ClothDebrisTest {
  @Test
  void ragdollFallsAndTumbles() {
    ClothDebris debris =
        new ClothDebris(
            "minecraft:white_wool",
            new Vector3d(0, 10, 0),
            new Vector3d(2, 0, 0),
            new Vector3d(0, 3, 0));
    World world =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -10, 0);
          }

          public dev.mintychochip.phys.FluidField fluidField() {
            return new dev.mintychochip.phys.FluidField() {
              public boolean isFluid(org.joml.Vector3dc p) {
                return false;
              }

              public double density(org.joml.Vector3dc p) {
                return 0;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };
    double y0 = debris.position().y();
    double spin0 = debris.angularVelocity().length();
    debris.step(world, new PhysicsEngine());
    assertTrue(debris.position().y() < y0);
    assertTrue(debris.angularVelocity().length() < spin0);
  }
}
