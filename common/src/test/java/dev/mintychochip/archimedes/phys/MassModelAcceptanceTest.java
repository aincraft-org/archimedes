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
import dev.mintychochip.phys.FluidField;
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
    Ship lightShip = ship(lightBlocks);
    Ship heavyShip = ship(heavyBlocks);

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

    EquilibriumSolver solver = new EquilibriumSolver();
    World world = waterWorld(20.0, 10.0);

    EquilibriumResult lightResult =
        solver.solve(
            ShipBody.from(lightShip, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
            world,
            config);
    EquilibriumResult heavyResult =
        solver.solve(
            ShipBody.from(heavyShip, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
            world,
            config);

    assertTrue(lightResult.equilibrium(), "light ship reaches equilibrium");
    assertTrue(heavyResult.equilibrium(), "heavy ship reaches equilibrium");
    assertTrue(
        heavyResult.targetY() < lightResult.targetY(), "heavy ship settles at a deeper draft");
  }

  @Test
  void a2MixedMaterialAggregateMassAndEquilibrium() {
    List<ShipBlock> blocks = new ArrayList<>(15);
    for (int i = 0; i < 10; i++) blocks.add(new ShipBlock(new BlockPos(0, i, 0), "light"));
    for (int i = 10; i < 15; i++) blocks.add(new ShipBlock(new BlockPos(0, i, 0), "heavy"));
    Ship ship = ship(blocks);

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

    EquilibriumResult result =
        new EquilibriumSolver()
            .solve(
                ShipBody.from(ship, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
                waterWorld(20.0, 1000.0),
                config);
    assertTrue(result.equilibrium(), "mixed-material ship reaches equilibrium");
    assertEquals(5.0, result.targetY(), 1e-9);
  }

  @Test
  void a3UnknownMaterialUsesDefaultDensity() {
    Ship ship = ship(stackOf("unknown:custom", 10));

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

    EquilibriumResult result =
        new EquilibriumSolver()
            .solve(
                ShipBody.from(ship, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
                waterWorld(20.0, 1000.0),
                config);
    assertTrue(result.equilibrium(), "ship with unknown material reaches equilibrium");
  }

  @Test
  void a6OnlyTrackedPlayersContributeToRiderMass() {
    Ship ship = ship(stackOf("light", 10));

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
    Ship ship = ship(stackOf("light", 10));

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

    EquilibriumSolver solver = new EquilibriumSolver();
    World world = waterWorld(20.0, 1000.0);

    EquilibriumResult noRider =
        solver.solve(
            ShipBody.from(ship, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
            world,
            config);
    EquilibriumResult oneRider =
        solver.solve(
            ShipBody.from(ship, b -> b.blockData(), config, 1, new ShipBuoyancyForce()),
            world,
            config);

    assertTrue(noRider.equilibrium(), "stable without rider");
    assertTrue(oneRider.equilibrium(), "stable with one rider");
    assertTrue(oneRider.targetY() < noRider.targetY(), "boarding deepens draft");
  }

  @Test
  void a8UnboardingShallowerEquilibriumDraft() {
    Ship ship = ship(stackOf("light", 10));

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

    EquilibriumSolver solver = new EquilibriumSolver();
    World world = waterWorld(20.0, 1000.0);

    EquilibriumResult oneRider =
        solver.solve(
            ShipBody.from(ship, b -> b.blockData(), config, 1, new ShipBuoyancyForce()),
            world,
            config);
    EquilibriumResult noRider =
        solver.solve(
            ShipBody.from(ship, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
            world,
            config);

    assertTrue(oneRider.equilibrium(), "stable with one rider");
    assertTrue(noRider.equilibrium(), "stable after unboarding");
    assertTrue(noRider.targetY() > oneRider.targetY(), "unboarding raises draft");
  }

  @Test
  void a10OverloadedShipReportsSinkingWithoutThrowing() {
    Ship ship = ship(stackOf("heavy", 5));

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

    EquilibriumResult result =
        new EquilibriumSolver()
            .solve(
                ShipBody.from(ship, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
                waterWorld(20.0, 1000.0),
                config);
    assertEquals(1500.0, ShipMassModel.mass(ship, b -> b.blockData(), config, 0), 1e-9);
    assertEquals(false, result.equilibrium(), "overloaded ship has no equilibrium in bounds");
  }

  @Test
  void a11NoWaterHoldsPoseWithoutFabricatingTarget() {
    Ship ship = ship(stackOf("light", 10));

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
            80.0,
            32.0,
            1e-6,
            1e-3);

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

    EquilibriumResult result =
        new EquilibriumSolver()
            .solve(
                ShipBody.from(ship, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
                dryWorld,
                config);
    assertEquals(false, result.equilibrium(), "no water means no equilibrium");
  }

  @Test
  void a12SolverStaysInsideConfiguredBoundsAndMeetsTolerance() {
    Ship ship = ship(stackOf("medium", 10));

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

    EquilibriumResult result =
        new EquilibriumSolver()
            .solve(
                ShipBody.from(ship, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
                waterWorld(20.0, 1000.0),
                config);
    assertTrue(result.equilibrium(), "solution within bounds");
    assertEquals(5.0, result.targetY(), 1e-9);
    assertTrue(Math.abs(result.residual()) <= config.massTolerance(), "residual within tolerance");
  }

  @Test
  void a14LargerFootprintDampensDraftChangeForSameLoadDelta() {
    List<ShipBlock> narrow = stackOf("medium", 10);
    List<ShipBlock> broad = prismOf("medium", 2, 10);

    Ship narrowShip = ship(narrow);
    Ship broadShip = ship(broad);

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

    EquilibriumSolver solver = new EquilibriumSolver();
    World world = waterWorld(20.0, 1000.0);

    EquilibriumResult narrowNoRider =
        solver.solve(
            ShipBody.from(narrowShip, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
            world,
            config);
    EquilibriumResult narrowWithRider =
        solver.solve(
            ShipBody.from(narrowShip, b -> b.blockData(), config, 1, new ShipBuoyancyForce()),
            world,
            config);
    EquilibriumResult broadNoRider =
        solver.solve(
            ShipBody.from(broadShip, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
            world,
            config);
    EquilibriumResult broadWithRider =
        solver.solve(
            ShipBody.from(broadShip, b -> b.blockData(), config, 1, new ShipBuoyancyForce()),
            world,
            config);

    assertTrue(narrowNoRider.equilibrium(), "narrow ship floats");
    assertTrue(narrowWithRider.equilibrium(), "narrow ship floats with rider");
    assertTrue(broadNoRider.equilibrium(), "broad ship floats");
    assertTrue(broadWithRider.equilibrium(), "broad ship floats with rider");

    double narrowDelta = narrowWithRider.targetY() - narrowNoRider.targetY();
    double broadDelta = broadWithRider.targetY() - broadNoRider.targetY();
    assertTrue(
        Math.abs(narrowDelta) > Math.abs(broadDelta),
        "larger footprint changes draft less for the same rider load");
  }

  @Test
  void a13DetectsNonMonotonicFluidAndHoldsPose() {
    Ship ship =
        new Ship(
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

    EquilibriumSolver solver = new EquilibriumSolver();
    World world = thresholdWaterWorld(49.0, 1000.0);

    EquilibriumResult result =
        solver.solve(
            ShipBody.from(ship, b -> b.blockData(), config, 0, new ShipBuoyancyForce()),
            world,
            config);

    assertFalse(result.equilibrium(), "non-monotonic surface must not fabricate an equilibrium");
    assertTrue(
        result.reason().contains("unstable"),
        "expected unstable sample reason, got: " + result.reason());
  }

  private static Ship ship(List<ShipBlock> blocks) {
    return new Ship(
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
