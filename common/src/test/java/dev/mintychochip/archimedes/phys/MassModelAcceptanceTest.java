package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class MassModelAcceptanceTest {
  @Test
  void a1EqualVolumeDifferentMaterialsDeeperDraft() {
    List<ShipBlock> lightBlocks = stackOf("light", 20);
    List<ShipBlock> heavyBlocks = stackOf("heavy", 20);
    Vehicle lightShip = ship(lightBlocks);
    Vehicle heavyShip = ship(heavyBlocks);

    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            32.0,
            0.05,
            10.0,
            0.5,
            0.9,
            Map.of("light", 0.5, "heavy", 2.0),
            1.0,
            80.0,
            32.0,
            1e-6,
            1e-3);

    double lightMass = ShipMassModel.mass(lightShip, b -> b.blockData(), config, 0);
    double heavyMass = ShipMassModel.mass(heavyShip, b -> b.blockData(), config, 0);
    assertEquals(10.0, lightMass, 1e-9);
    assertEquals(40.0, heavyMass, 1e-9);
    assertTrue(heavyMass > lightMass, "heavy material has greater block mass");

    World world = waterWorld(20.0, 10.0);
    double lightY = settle(lightShip, config, world, 0);
    double heavyY = settle(heavyShip, config, world, 0);
    assertTrue(heavyY < lightY, "heavy ship settles at a deeper draft");
  }

  @Test
  void a2MixedMaterialAggregateMassAndEquilibrium() {
    List<ShipBlock> blocks = new ArrayList<>(15);
    for (int i = 0; i < 10; i++) {
      blocks.add(new ShipBlock(new BlockPos(0, i, 0), "light"));
    }
    for (int i = 10; i < 15; i++) {
      blocks.add(new ShipBlock(new BlockPos(0, i, 0), "heavy"));
    }
    Vehicle ship = ship(blocks);

    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            32.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("light", 200.0, "heavy", 800.0),
            1.0,
            80.0,
            32.0,
            1e-6,
            1e-3);

    double mass = ShipMassModel.mass(ship, b -> b.blockData(), config, 0);
    assertEquals(6000.0, mass, 1e-9);
    settle(ship, config, waterWorld(20.0, 1000.0), 0);
    assertTrue(Double.isFinite(ship.pose().y()), "mixed-material ship is stepped by the engine");
  }

  @Test
  void a3UnknownMaterialUsesDefaultDensity() {
    Vehicle ship = ship(stackOf("unknown:custom", 10));

    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            32.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("light", 200.0),
            400.0,
            80.0,
            32.0,
            1e-6,
            1e-3);

    double mass = ShipMassModel.mass(ship, b -> b.blockData(), config, 0);
    assertEquals(4000.0, mass, 1e-9);
    settle(ship, config, waterWorld(20.0, 1000.0), 0);
    assertTrue(Double.isFinite(ship.pose().y()));
  }

  @Test
  void a6OnlyTrackedPlayersContributeToRiderMass() {
    Vehicle ship = ship(stackOf("light", 10));

    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            32.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("light", 200.0),
            1.0,
            1000.0,
            32.0,
            1e-6,
            1e-3);

    assertEquals(2000.0, ShipMassModel.mass(ship, b -> b.blockData(), config, 0), 1e-9);
    assertEquals(3000.0, ShipMassModel.mass(ship, b -> b.blockData(), config, 1), 1e-9);
    assertEquals(4000.0, ShipMassModel.mass(ship, b -> b.blockData(), config, 2), 1e-9);
  }

  @Test
  void a7BoardingDeepensEquilibriumDraft() {
    ShipConfig config = riderConfig();
    World world = waterWorld(20.0, 1000.0);
    Vehicle noRider = ship(stackOf("light", 10));
    Vehicle oneRider = ship(stackOf("light", 10));
    double emptyY = settle(noRider, config, world, 0);
    double boardedY = settle(oneRider, config, world, 1);
    assertTrue(boardedY < emptyY, "boarding deepens draft");
  }

  @Test
  void a8UnboardingShallowerEquilibriumDraft() {
    ShipConfig config = riderConfig();
    World world = waterWorld(20.0, 1000.0);
    Vehicle boarded = ship(stackOf("light", 10));
    Vehicle empty = ship(stackOf("light", 10));
    double boardedY = settle(boarded, config, world, 1);
    double emptyY = settle(empty, config, world, 0);
    assertTrue(emptyY > boardedY, "unboarding raises draft");
  }

  @Test
  void a10OverloadedShipReportsSinkingWithoutThrowing() {
    Vehicle ship = ship(stackOf("heavy", 5));
    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            32.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("heavy", 300.0),
            1.0,
            80.0,
            32.0,
            1e-6,
            1e-3);

    assertEquals(1500.0, ShipMassModel.mass(ship, b -> b.blockData(), config, 0), 1e-9);
    Vehicle tooHeavy = ship(stackOf("ballast", 5));
    ShipConfig ballast =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            32.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("ballast", 3000.0),
            1.0,
            80.0,
            32.0,
            1e-6,
            1e-3);
    double start = tooHeavy.pose().y();
    double y = settle(tooHeavy, ballast, waterWorld(20.0, 1000.0), 0);
    assertTrue(y < start, "overloaded ship sinks under gravity");
  }

  @Test
  void a11NoWaterHoldsPoseWithoutFabricatingTarget() {
    Vehicle ship = ship(stackOf("light", 10));
    ShipConfig config = riderConfig();
    World dryWorld =
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
                return 1000.0;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };

    ShipPhysics physics = physics(config, dryWorld, s -> 0);
    assertFalse(physics.rise(ship), "no water means rise does not invent a float pose");
    double before = ship.pose().y();
    physics.tick(ship);
    assertTrue(ship.pose().y() <= before, "dry tick must not raise the ship");
  }

  @Test
  void a12SolverStaysInsideConfiguredBoundsAndMeetsTolerance() {
    Vehicle ship = ship(stackOf("medium", 10));
    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            5.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("medium", 600.0),
            1.0,
            80.0,
            5.0,
            1e-6,
            1e-3);
    double start = ship.pose().y();
    ShipPhysics physics = physics(config, waterWorld(20.0, 1000.0), s -> 0);
    physics.tick(ship);
    assertTrue(ship.pose().y() <= start + config.maxRise());
    assertTrue(ship.pose().y() >= start - config.maxFall());
  }

  @Test
  void a14LargerFootprintDampensDraftChangeForSameLoadDelta() {
    Vehicle narrowShip = ship(stackOf("medium", 10));
    Vehicle broadShip = ship(prismOf("medium", 2, 10));
    ShipConfig config =
        new ShipConfig(
            2048,
            8,
            Set.of(),
            Set.of(),
            true,
            1,
            0.5,
            32.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("medium", 500.0),
            1.0,
            4000.0,
            32.0,
            1e-6,
            1e-3);
    World world = waterWorld(20.0, 1000.0);
    double narrowDelta =
        settle(ship(stackOf("medium", 10)), config, world, 1)
            - settle(narrowShip, config, world, 0);
    double broadDelta =
        settle(ship(prismOf("medium", 2, 10)), config, world, 1)
            - settle(broadShip, config, world, 0);
    assertTrue(
        Math.abs(narrowDelta) > Math.abs(broadDelta),
        "larger footprint changes draft less for the same rider load");
  }

  @Test
  void a13DetectsNonMonotonicFluidAndHoldsPose() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "light")),
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
            80.0,
            0.05,
            1000.0,
            0.5,
            0.9,
            Map.of("light", 1.0),
            1.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    ShipPhysics physics = physics(config, thresholdWaterWorld(49.0, 1000.0), s -> 0);
    physics.tick(ship);
    assertTrue(Double.isFinite(ship.pose().y()), "odd fluid fields must not throw");
  }

  private static double settle(Vehicle ship, ShipConfig config, World world, int riders) {
    ShipPhysics physics = physics(config, world, s -> riders);
    for (int i = 0; i < 80; i++) {
      physics.tick(ship);
    }
    return ship.pose().y();
  }

  private static ShipPhysics physics(ShipConfig config, World world, RiderCount riders) {
    return new ShipPhysicsImpl(
        new PhysicsEngine(), world, config, b -> b.blockData(), noopRuntime(), riders);
  }

  private static ShipRuntime noopRuntime() {
    return new ShipRuntime() {
      public void spawn(Vehicle s) {}

      public void move(Vehicle s, double oldY, double newY) {}

      public void remove(Vehicle s) {}

      public void removeAll(java.util.Collection<Vehicle> s) {}
    };
  }

  private static ShipConfig riderConfig() {
    return new ShipConfig(
        2048,
        8,
        Set.of(),
        Set.of(),
        true,
        1,
        0.5,
        32.0,
        0.05,
        1000.0,
        0.5,
        0.9,
        Map.of("light", 200.0),
        1.0,
        1000.0,
        32.0,
        1e-6,
        1e-3);
  }

  private static Vehicle ship(List<ShipBlock> blocks) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        blocks,
        new ShipPose(10),
        true);
  }

  private static List<ShipBlock> stackOf(String material, int count) {
    List<ShipBlock> blocks = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      blocks.add(new ShipBlock(new BlockPos(0, i, 0), material));
    }
    return blocks;
  }

  private static List<ShipBlock> prismOf(String material, int footprint, int height) {
    List<ShipBlock> blocks = new ArrayList<>(footprint * footprint * height);
    for (int x = 0; x < footprint; x++) {
      for (int z = 0; z < footprint; z++) {
        for (int y = 0; y < height; y++) {
          blocks.add(new ShipBlock(new BlockPos(x, y, z), material));
        }
      }
    }
    return blocks;
  }

  private static World waterWorld(double surfaceY, double density) {
    return new World() {
      public Vector3d gravity() {
        return new Vector3d(0, -10, 0);
      }

      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3dc p) {
            return p.y() <= surfaceY + 0.5;
          }

          public double density(Vector3dc p) {
            return density;
          }
        };
      }

      public double timeStep() {
        return 0.05;
      }
    };
  }

  private static World thresholdWaterWorld(double threshold, double density) {
    return new World() {
      public Vector3d gravity() {
        return new Vector3d(0, -10, 0);
      }

      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3dc p) {
            return p.y() > threshold;
          }

          public double density(Vector3dc p) {
            return density;
          }
        };
      }

      public double timeStep() {
        return 0.05;
      }
    };
  }
}
