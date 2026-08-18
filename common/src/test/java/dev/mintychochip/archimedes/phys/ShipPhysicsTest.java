package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.World;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class ShipPhysicsTest {
  private static final String OAK_PLANKS = "minecraft:oak_planks";
  private static final String WHITE_WOOL = "minecraft:white_wool";

  @Test
  void tickRestoresPoseOnBlockedMove() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), OAK_PLANKS)),
            new ShipPose(0),
            true);
    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            16.0,
            0.05,
            1.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 600.0),
            1000.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World world =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -10, 0);
          }

          public FluidField fluidField() {
            return new FluidField() {
              public boolean isFluid(Vector3dc p) {
                return true;
              }

              public double density(Vector3dc p) {
                return 1000;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Ship s) {}

          public void move(Ship s, double oldY, double newY) {
            throw new IllegalStateException("blocked");
          }

          public void remove(Ship s) {}

          public void removeAll(java.util.Collection<Ship> s) {}
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(), world, config, new BukkitLikeResolver(), runtime, s -> 0);
    ShipPose old = ship.pose();
    assertFalse(physics.tick(ship));
    assertEquals(old.y(), ship.pose().y(), 1e-9);
  }

  @Test
  void tickMovesForwardWhenClothFacesTheWind() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL)),
            new ShipPose(0),
            true);
    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            16.0,
            0.05,
            1.0,
            0.5,
            0.9,
            Map.of(WHITE_WOOL, 1.0),
            1.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World world =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, 0, 0);
          }

          public FluidField fluidField() {
            return new FluidField() {
              public boolean isFluid(Vector3dc p) {
                return false;
              }

              public double density(Vector3dc p) {
                return 0;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Ship s) {}

          public void move(Ship s, double oldY, double newY) {}

          public void remove(Ship s) {}

          public void removeAll(java.util.Collection<Ship> s) {}
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            world,
            config,
            new BukkitLikeResolver(),
            runtime,
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));

    assertTrue(physics.tick(ship));
    assertTrue(ship.pose().z() > 0);
  }

  @Test
  void tickFallsUnderGravityWhenThereIsNoWaterInsteadOfHoldingAnEquilibrium() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), OAK_PLANKS)),
            new ShipPose(0),
            true);
    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.01,
            16.0,
            0.05,
            1.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 600.0),
            1000.0,
            80.0,
            16.0,
            0.0,
            1e-3);
    World dry =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -10, 0);
          }

          public FluidField fluidField() {
            return new FluidField() {
              public boolean isFluid(Vector3dc p) {
                return false;
              }

              public double density(Vector3dc p) {
                return 1000;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Ship s) {}

          public void move(Ship s, double oldY, double newY) {}

          public void remove(Ship s) {}

          public void removeAll(java.util.Collection<Ship> s) {}
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(), dry, config, new BukkitLikeResolver(), runtime, s -> 0);

    assertTrue(physics.tick(ship));
    assertTrue(ship.pose().y() < -0.01, "gravity must be able to drop more than bob-amplitude");
  }

  @Test
  void dryClothShipFallsAndDriftsDownwindInsteadOfHovering() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 40, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), WHITE_WOOL)),
            new ShipPose(0),
            true);
    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.01,
            16.0,
            0.05,
            1.0,
            0.5,
            0.9,
            Map.of(WHITE_WOOL, 1.0),
            1.0,
            80.0,
            16.0,
            0.0,
            1e-3);
    World dry =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -10, 0);
          }

          public FluidField fluidField() {
            return new FluidField() {
              public boolean isFluid(Vector3dc p) {
                return false;
              }

              public double density(Vector3dc p) {
                return 0;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Ship s) {}

          public void move(Ship s, double oldY, double newY) {}

          public void remove(Ship s) {}

          public void removeAll(java.util.Collection<Ship> s) {}
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            dry,
            config,
            new BukkitLikeResolver(),
            runtime,
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));

    assertTrue(physics.tick(ship));
    assertTrue(ship.pose().y() < 0, "ship client has no aerostatic envelope");
    assertTrue(ship.pose().z() > 0, "cloth still catches the wind while falling");
  }

  @Test
  void tickDoesNotApplyPhysicsWhenAShipChunkIsUnloaded() {
    Ship ship =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 32, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL)),
            new ShipPose(0),
            true);
    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            16.0,
            0.05,
            1.0,
            0.5,
            0.9,
            Map.of(WHITE_WOOL, 1.0),
            1.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    java.util.List<String> chunkQueries = new java.util.ArrayList<>();
    java.util.concurrent.atomic.AtomicInteger moves =
        new java.util.concurrent.atomic.AtomicInteger();
    World world =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, 0, 0);
          }

          public FluidField fluidField() {
            return new FluidField() {
              public boolean isFluid(Vector3dc p) {
                return false;
              }

              public double density(Vector3dc p) {
                return 0;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }

          public boolean isChunkLoaded(int chunkX, int chunkZ) {
            chunkQueries.add(chunkX + "," + chunkZ);
            return false;
          }
        };
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Ship s) {}

          public void move(Ship s, double oldY, double newY) {
            moves.incrementAndGet();
          }

          public void remove(Ship s) {}

          public void removeAll(java.util.Collection<Ship> s) {}
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            world,
            config,
            new BukkitLikeResolver(),
            runtime,
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));

    assertFalse(physics.tick(ship));
    assertEquals(0.0, ship.pose().z(), 0.0);
    assertEquals(0, moves.get());
    assertTrue(chunkQueries.contains("2,0"), "origin 32 is chunk 2");
  }

  static final class BukkitLikeResolver implements MaterialKeyResolver {
    public String key(ShipBlock block) {
      return block.blockData();
    }
  }
}
