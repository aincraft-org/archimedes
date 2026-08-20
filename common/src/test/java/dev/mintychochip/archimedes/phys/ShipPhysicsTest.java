package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.sail.SailShipTemplate;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.GravityForce;
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
  private static final String FURNACE_SOUTH = "minecraft:furnace[facing=south]";

  @Test
  void tickRestoresPoseOnBlockedMove() {
    Vehicle ship =
        new Vehicle(
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
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {
            throw new IllegalStateException("blocked");
          }

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
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
    Vehicle ship =
        new Vehicle(
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
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
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
  void tickStillSailsWhenTheSeafloorBlocksAGravityStep() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            SailShipTemplate.blocks(),
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
            10.0,
            10.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 6.0, SailShipTemplate.MAST, 7.0, SailShipTemplate.SAIL, 1.0),
            10.0,
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
                return p.y() >= 10 && p.y() < 11;
              }

              public double density(Vector3dc p) {
                return p.y() >= 10 && p.y() < 11 ? 10.0 : 0.0;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }

          public boolean isObstacle(Vector3dc point) {
            return point.y() < 10;
          }
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            world,
            config,
            new BukkitLikeResolver(),
            new ShipRuntime() {
              public void spawn(Vehicle s) {}

              public void move(Vehicle s, double oldY, double newY) {}

              public void remove(Vehicle s) {}

              public void removeAll(java.util.Collection<Vehicle> s) {}
            },
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 8)));
    for (int i = 0; i < 8; i++) {
      physics.tick(ship);
    }
    assertTrue(ship.pose().z() > 0.01, "sail XZ must not be discarded when Y is blocked");
  }

  @Test
  void tickStillSailsWhenStandingOnTheGround() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            SailShipTemplate.blocks(),
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
            10.0,
            10.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 6.0, SailShipTemplate.MAST, 7.0, SailShipTemplate.SAIL, 1.0),
            10.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World ground =
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

          public boolean isObstacle(Vector3dc point) {
            return point.y() < 10;
          }
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            ground,
            config,
            new BukkitLikeResolver(),
            new ShipRuntime() {
              public void spawn(Vehicle s) {}

              public void move(Vehicle s, double oldY, double newY) {}

              public void remove(Vehicle s) {}

              public void removeAll(java.util.Collection<Vehicle> s) {}
            },
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 8)));
    for (int i = 0; i < 8; i++) {
      physics.tick(ship);
    }
    assertTrue(
        ship.pose().z() > 0.01, "grounded hull must still take sail XZ; z=" + ship.pose().z());
  }

  @Test
  void tickStillSailsWhenTheDeckOverlapsTheSeafloor() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            SailShipTemplate.blocks(),
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
            10.0,
            10.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 6.0, SailShipTemplate.MAST, 7.0, SailShipTemplate.SAIL, 1.0),
            10.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World embedded =
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

          public boolean isObstacle(Vector3dc point) {
            return point.y() <= 10.5;
          }
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            embedded,
            config,
            new BukkitLikeResolver(),
            new ShipRuntime() {
              public void spawn(Vehicle s) {}

              public void move(Vehicle s, double oldY, double newY) {}

              public void remove(Vehicle s) {}

              public void removeAll(java.util.Collection<Vehicle> s) {}
            },
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 8)));
    for (int i = 0; i < 8; i++) {
      physics.tick(ship);
    }
    assertTrue(
        ship.pose().z() > 0.01, "keel overlap must not freeze sail drive; z=" + ship.pose().z());
  }

  @Test
  void tickFallsUnderGravityWhenThereIsNoWaterInsteadOfHoldingAnEquilibrium() {
    Vehicle ship =
        new Vehicle(
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
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(), dry, config, new BukkitLikeResolver(), runtime, s -> 0);

    assertTrue(physics.tick(ship));
    assertTrue(ship.pose().y() < -0.01, "gravity must be able to drop more than bob-amplitude");
  }

  @Test
  void riseDoesNotTeleportASurfaceSailIntoTheWater() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            SailShipTemplate.blocks(),
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
            10.0,
            10.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 6.0, SailShipTemplate.MAST, 7.0, SailShipTemplate.SAIL, 1.0),
            10.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World water =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -10, 0);
          }

          public FluidField fluidField() {
            return new FluidField() {
              public boolean isFluid(Vector3dc p) {
                return p.y() < 11;
              }

              public double density(Vector3dc p) {
                return p.y() < 11 ? 10.0 : 0.0;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            water,
            config,
            new BukkitLikeResolver(),
            new ShipRuntime() {
              public void spawn(Vehicle s) {}

              public void move(Vehicle s, double oldY, double newY) {}

              public void remove(Vehicle s) {}

              public void removeAll(java.util.Collection<Vehicle> s) {}
            },
            s -> 0);
    physics.rise(ship);
    assertTrue(ship.pose().y() > -2.0, "rise must not slam maxFall; y=" + ship.pose().y());
    assertTrue(ship.pose().y() < 3.0, "rise must not slam maxRise; y=" + ship.pose().y());
  }

  @Test
  void drySailFallsInsteadOfGlidingUnderPluginGravity() {
    double gravity = 10.0;
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 40, 0),
            SailShipTemplate.blocks(),
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
            gravity,
            1.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 0.6),
            1.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World dry =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -config.gravity(), 0);
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
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            dry,
            config,
            new BukkitLikeResolver(),
            new ShipRuntime() {
              public void spawn(Vehicle s) {}

              public void move(Vehicle s, double oldY, double newY) {}

              public void remove(Vehicle s) {}

              public void removeAll(java.util.Collection<Vehicle> s) {}
            },
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 8)));
    for (int i = 0; i < 20; i++) {
      physics.tick(ship);
    }
    assertTrue(ship.pose().y() < -1.0, "airborne hull must drop more than a block in one second");
    assertTrue(
        -ship.pose().y() > ship.pose().z() * 0.5,
        "fall must outpace sail glide; y=" + ship.pose().y() + " z=" + ship.pose().z());
  }

  @Test
  void dryClothShipFallsAndDriftsDownwindInsteadOfHovering() {
    Vehicle ship =
        new Vehicle(
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
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
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
    Vehicle ship =
        new Vehicle(
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
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {
            moves.incrementAndGet();
          }

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
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

  @Test
  void inspectReportsMassFactorsAndSampledForces() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            List.of(new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL)),
            new ShipPose(0, 0, 0),
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
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            world,
            config,
            new BukkitLikeResolver(),
            runtime,
            s -> 2,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));

    ShipInspection report = physics.inspect(ship);

    assertEquals(1, report.blocks());
    assertEquals(1, report.cloth());
    assertEquals(2, report.riders());
    assertTrue(report.mass() > 0);
    assertTrue(report.chunksLoaded());
    assertEquals(0, report.submerged());
    assertTrue(report.sampleNanos() >= 0);
    java.util.Map<String, ShipInspection.ForceLine> byName = new java.util.HashMap<>();
    for (ShipInspection.ForceLine line : report.forces()) {
      byName.put(line.name(), line);
    }
    assertTrue(byName.containsKey("Gravity"));
    assertTrue(byName.get("Gravity").fy() < 0);
    assertTrue(byName.containsKey("WaterDrag"));
    assertTrue(byName.containsKey("Drag"));
    ShipInspection.ForceLine sail = null;
    for (ShipInspection.ForceLine line : report.forces()) {
      if (line.name().startsWith("Sail +Z")) {
        sail = line;
      }
    }
    assertNotNull(sail);
    assertTrue(sail.fz() > 0);
    assertTrue(report.netFz() > 0);
    assertEquals(0.0, ship.pose().z(), 0.0);
    java.util.List<String> lines = ShipInspectionLines.lines(report);
    assertTrue(lines.get(0).contains("Arch "));
    assertTrue(lines.stream().anyMatch(line -> line.contains("Gravity")));
    assertTrue(lines.stream().anyMatch(line -> line.contains("Sail +Z")));
    assertTrue(lines.stream().anyMatch(line -> line.contains("net ")));
    assertTrue(lines.stream().anyMatch(line -> line.contains("sample=")));
    String wind = lines.stream().filter(line -> line.contains("wind=")).findFirst().orElse("");
    assertTrue(wind.contains("wind="), "inspect must report the sampled wind vector");
    assertTrue(wind.contains("\u00A7c0.00\u00A7r"), wind);
    assertTrue(wind.contains("\u00A7a0.00\u00A7r"), wind);
    assertTrue(wind.contains("\u00A7b10.00\u00A7r"), wind);
  }

  @Test
  void inspectReportsStillWindWhenSailForceIsZero() {
    ShipInspection report =
        sailPhysics(mediumWorld(false, 0), FlowField.still()).inspect(clothShip());
    java.util.List<String> lines = ShipInspectionLines.lines(report);
    String wind = lines.stream().filter(line -> line.contains("wind=")).findFirst().orElse("");
    assertTrue(wind.contains("wind="), wind);
    assertTrue(wind.contains("\u00A7c0.00\u00A7r"), wind);
    assertTrue(wind.contains("\u00A7a0.00\u00A7r"), wind);
    assertTrue(wind.contains("\u00A7b0.00\u00A7r"), wind);
    assertTrue(lines.stream().anyMatch(line -> line.contains("Sail ")));
    ShipInspection.ForceLine sail = sailLines(report).get(0);
    assertEquals(0.0, sail.fx(), 1e-9);
    assertEquals(0.0, sail.fy(), 1e-9);
    assertEquals(0.0, sail.fz(), 1e-9);
  }

  @Test
  void tickMovesAPluginScaleSmallSail() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 63, 0),
            SailShipTemplate.blocks(SailShipTemplate.Size.SMALL),
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
            10.0,
            10.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 6.0, SailShipTemplate.MAST, 7.0, SailShipTemplate.SAIL, 1.0),
            10.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World air =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -config.gravity(), 0);
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
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            air,
            config,
            new BukkitLikeResolver(),
            new ShipRuntime() {
              public void spawn(Vehicle s) {}

              public void move(Vehicle s, double oldY, double newY) {}

              public void remove(Vehicle s) {}

              public void removeAll(java.util.Collection<Vehicle> s) {}
            },
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 8)));
    for (int i = 0; i < 8; i++) {
      physics.tick(ship);
    }
    assertTrue(
        ship.pose().z() > 0.01, "plugin-scale small sail must still move; z=" + ship.pose().z());
  }

  @Test
  void displacedMassUsesOnlyTheWetFractionOfACell() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), OAK_PLANKS)),
            new ShipPose(0.75),
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
            10.0,
            10.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, 6.0),
            10.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World water =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, -10, 0);
          }

          public FluidField fluidField() {
            return new FluidField() {
              public boolean isFluid(Vector3dc p) {
                return p.y() < 1.0;
              }

              public double density(Vector3dc p) {
                return 10.0;
              }
            };
          }

          public double timeStep() {
            return 0.05;
          }
        };
    Body body = ShipBody.from(ship, new BukkitLikeResolver(), config, 0, new GravityForce());
    assertEquals(
        2.5,
        WaterlineResolver.displacedMass(body, water),
        1e-6,
        "a cell 25% under the free surface must not count as fully wet");
  }

  @Test
  void pluginScaleLargeSailSitsInTheWaterNotOnTop() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            SailShipTemplate.blocks(SailShipTemplate.Size.LARGE),
            new ShipPose(0),
            true);
    ShipConfig config = pluginScaleConfig();
    World ocean = pluginOcean(11.0);
    ShipPhysics physics = pluginPhysics(ocean, config, s -> 0);
    for (int i = 0; i < 80; i++) {
      physics.tick(ship);
    }
    assertTrue(
        ship.pose().y() < 0.4,
        "large deck must settle in the water, not float on a 1-block kiss; y=" + ship.pose().y());
  }

  @Test
  void pluginScalePlayerLoadDeepensLargeSailDraft() {
    Vehicle empty =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            SailShipTemplate.blocks(SailShipTemplate.Size.LARGE),
            new ShipPose(0),
            true);
    Vehicle boarded =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 10, 0),
            SailShipTemplate.blocks(SailShipTemplate.Size.LARGE),
            new ShipPose(0),
            true);
    ShipConfig config = pluginScaleConfig();
    World ocean = pluginOcean(11.0);
    ShipPhysics emptyPhysics = pluginPhysics(ocean, config, s -> 0);
    ShipPhysics boardedPhysics = pluginPhysics(ocean, config, s -> 1);
    for (int i = 0; i < 80; i++) {
      emptyPhysics.tick(empty);
      boardedPhysics.tick(boarded);
    }
    assertTrue(
        empty.pose().y() - boarded.pose().y() > 0.1,
        "one player must dip a large deck; empty="
            + empty.pose().y()
            + " boarded="
            + boarded.pose().y());
  }

  @Test
  void pluginScaleWeightExceedsRestSailForce() {
    double oakDensity = 6.0;
    double defaultDensity = 10.0;
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            SailShipTemplate.blocks(),
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
            10.0,
            10.0,
            0.5,
            0.9,
            Map.of(OAK_PLANKS, oakDensity),
            defaultDensity,
            80.0,
            16.0,
            1e-6,
            1e-3);
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            new World() {
              public Vector3d gravity() {
                return new Vector3d(0, -config.gravity(), 0);
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
            },
            config,
            new BukkitLikeResolver(),
            new ShipRuntime() {
              public void spawn(Vehicle s) {}

              public void move(Vehicle s, double oldY, double newY) {}

              public void remove(Vehicle s) {}

              public void removeAll(java.util.Collection<Vehicle> s) {}
            },
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 8)));
    ShipInspection report = physics.inspect(ship);
    ShipInspection.ForceLine gravity = null;
    double sail = 0;
    for (ShipInspection.ForceLine line : report.forces()) {
      if ("Gravity".equals(line.name())) {
        gravity = line;
      }
      if (line.name().startsWith("Sail")) {
        sail += Math.sqrt(line.fx() * line.fx() + line.fy() * line.fy() + line.fz() * line.fz());
      }
    }
    assertNotNull(gravity);
    assertTrue(
        Math.abs(gravity.fy()) > sail,
        "weight " + Math.abs(gravity.fy()) + " should exceed sail " + sail);
  }

  @Test
  void tickStillMovesThroughVegetationInsteadOfStopping() {
    Vehicle ship =
        new Vehicle(
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
    World kelp =
        new World() {
          public Vector3d gravity() {
            return new Vector3d(0, 0, 0);
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

          public boolean isObstacle(Vector3dc point) {
            return false;
          }

          public double vegetation(Vector3dc point) {
            return 1.0;
          }
        };
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            kelp,
            config,
            new BukkitLikeResolver(),
            runtime,
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));

    assertTrue(WaterlineResolver.isPathClear(ship, kelp, new ShipPose(0, 0, 2), config));
    assertTrue(physics.tick(ship));
    assertTrue(ship.pose().z() > 0);
  }

  @Test
  void inspectMergesSameFacingSailsIntoOneVector() {
    Vehicle one =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL)),
            new ShipPose(0),
            true);
    Vehicle four =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(
                new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL),
                new ShipBlock(new BlockPos(1, 1, 0), WHITE_WOOL),
                new ShipBlock(new BlockPos(0, 2, 0), WHITE_WOOL),
                new ShipBlock(new BlockPos(1, 2, 0), WHITE_WOOL)),
            new ShipPose(0),
            true);
    ShipPhysics physics = sailPhysics(mediumWorld(false, 0));
    java.util.List<ShipInspection.ForceLine> oneSails = sailLines(physics.inspect(one));
    java.util.List<ShipInspection.ForceLine> fourSails = sailLines(physics.inspect(four));
    assertEquals(1, oneSails.size());
    assertEquals(1, fourSails.size());
    assertTrue(fourSails.get(0).name().contains("4m2"));
    assertEquals(4.0, fourSails.get(0).fz() / oneSails.get(0).fz(), 1e-6);
  }

  @Test
  void inspectKeepsDifferentSailFacingsAsSeparateVectors() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(
                new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL),
                new ShipBlock(new BlockPos(1, 1, 0), "minecraft:white_wall_banner[facing=west]")),
            new ShipPose(0),
            true);
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            mediumWorld(false, 0),
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
                Map.of(WHITE_WOOL, 1.0, "minecraft:white_wall_banner", 1.0),
                1.0,
                80.0,
                16.0,
                1e-6,
                1e-3),
            block -> {
              String data = block.blockData();
              int bracket = data.indexOf('[');
              return bracket < 0 ? data : data.substring(0, bracket);
            },
            new ShipRuntime() {
              public void spawn(Vehicle s) {}

              public void move(Vehicle s, double oldY, double newY) {}

              public void remove(Vehicle s) {}

              public void removeAll(java.util.Collection<Vehicle> s) {}
            },
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.compose(
                FlowField.uniform(new Vector3d(0, 0, 10)),
                FlowField.uniform(new Vector3d(-10, 0, 0))));
    java.util.List<ShipInspection.ForceLine> sails = sailLines(physics.inspect(ship));
    assertEquals(2, sails.size());
    assertTrue(sails.stream().anyMatch(line -> line.name().contains("+Z") && line.fz() > 0));
    assertTrue(sails.stream().anyMatch(line -> line.name().contains("-X") && line.fx() < 0));
  }

  @Test
  void waterDragSlowsASailMoreThanAir() {
    Vehicle dryShip = clothShip();
    Vehicle wetShip = clothShip();
    World dry = mediumWorld(false, 0);
    World wet = mediumWorld(true, 1.0);
    ShipPhysics dryPhysics = sailPhysics(dry);
    ShipPhysics wetPhysics = sailPhysics(wet);
    for (int i = 0; i < 8; i++) {
      dryPhysics.tick(dryShip);
      wetPhysics.tick(wetShip);
    }
    assertTrue(dryShip.pose().z() > 0);
    assertTrue(dryShip.pose().z() > wetShip.pose().z() + 0.01);
    java.util.Map<String, ShipInspection.ForceLine> dryForces = forcesByName(dryPhysics, dryShip);
    java.util.Map<String, ShipInspection.ForceLine> wetForces = forcesByName(wetPhysics, wetShip);
    assertEquals(0.0, dryForces.get("WaterDrag").fz(), 1e-9);
    assertTrue(wetForces.get("WaterDrag").fz() < 0);
  }

  @Test
  void furnaceFacingSouthAdvancesPoseZOnTick() {
    Vehicle ship = furnaceShip();
    ShipPhysics physics = enginePhysics(mediumWorld(true, 1000), DensityField.uniform(1.2));
    assertTrue(physics.tick(ship));
    assertTrue(ship.pose().z() > 0, "density-scaled engine thrust must drive the live tick");
  }

  @Test
  void vacuumEngineTickDoesNotAdvanceWhileDenseMediumDoes() {
    Vehicle vacuumHull = furnaceShip();
    Vehicle denseHull = furnaceShip();
    ShipPhysics vacuum = enginePhysics(mediumWorld(false, 0), DensityField.uniform(0));
    ShipPhysics dense = enginePhysics(mediumWorld(true, 1000), DensityField.uniform(1.2));
    vacuum.tick(vacuumHull);
    dense.tick(denseHull);
    assertEquals(0.0, vacuumHull.pose().z(), 1e-6);
    assertTrue(denseHull.pose().z() > vacuumHull.pose().z() + 0.01);
  }

  @Test
  void enginesOffKeepMassAndDropThrustOnLiveInspect() {
    Vehicle ship = furnaceShip();
    ShipPhysics physics = enginePhysics(mediumWorld(true, 1000), DensityField.uniform(1.2));
    double massOn = physics.inspect(ship).mass();
    assertTrue(
        physics.inspect(ship).forces().stream()
            .anyMatch(line -> line.name().contains("MediumThrust")));
    ship.setEnginesEnabled(false);
    ShipInspection off = physics.inspect(ship);
    assertEquals(massOn, off.mass(), 1e-9);
    assertFalse(off.forces().stream().anyMatch(line -> line.name().contains("MediumThrust")));
  }

  @Test
  void unsupportedWoolTearsOffInStrongWind() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(
                new ShipBlock(new BlockPos(0, 0, 0), OAK_PLANKS),
                new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL),
                new ShipBlock(new BlockPos(0, 2, 0), WHITE_WOOL),
                new ShipBlock(new BlockPos(0, 3, 0), WHITE_WOOL)),
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
            Map.of(OAK_PLANKS, 6.0, WHITE_WOOL, 1.0),
            10.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    World world = mediumWorld(false, 0);
    java.util.List<UUID> spawned = new java.util.ArrayList<>();
    java.util.List<UUID> moved = new java.util.ArrayList<>();
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}

          public void spawnClothRagdoll(
              Vehicle s, UUID debrisId, String appearance, double x, double y, double z) {
            spawned.add(debrisId);
          }

          public void moveClothRagdoll(
              UUID debrisId,
              double x,
              double y,
              double z,
              double qx,
              double qy,
              double qz,
              double qw) {
            moved.add(debrisId);
          }
        };
    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(),
            world,
            config,
            new EngineKeyResolver(),
            runtime,
            s -> 0,
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));
    assertEquals(0, SailRigging.distanceToRigid(ship, new BlockPos(0, 1, 0)));
    assertEquals(2, SailRigging.distanceToRigid(ship, new BlockPos(0, 3, 0)));
    double massBefore = physics.inspect(ship).mass();
    physics.tick(ship);
    assertFalse(ship.isTorn(new BlockPos(0, 1, 0)), "mast-adjacent cloth holds");
    assertTrue(ship.isTorn(new BlockPos(0, 2, 0)), "mid cloth snaps");
    assertTrue(ship.isTorn(new BlockPos(0, 3, 0)), "far cloth snaps");
    assertTrue(physics.inspect(ship).mass() < massBefore);
    assertEquals(2, spawned.size());
    assertEquals(spawned, moved);
  }

  private static Vehicle furnaceShip() {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), FURNACE_SOUTH)),
        new ShipPose(0),
        true);
  }

  private static ShipPhysics enginePhysics(World world, DensityField air) {
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
            Map.of("minecraft:furnace", 10.0),
            10.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
        };
    return new ShipPhysicsImpl(
        new PhysicsEngine(),
        world,
        config,
        new EngineKeyResolver(),
        runtime,
        s -> 0,
        air,
        FlowField.still());
  }

  private static Vehicle clothShip() {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL)),
        new ShipPose(0),
        true);
  }

  private static World mediumWorld(boolean liquid, double density) {
    return new World() {
      public Vector3d gravity() {
        return new Vector3d();
      }

      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3dc p) {
            return liquid;
          }

          public double density(Vector3dc p) {
            return liquid ? density : 0;
          }
        };
      }

      public double timeStep() {
        return 0.05;
      }
    };
  }

  private static ShipPhysics sailPhysics(World world) {
    return sailPhysics(world, FlowField.uniform(new Vector3d(0, 0, 10)));
  }

  private static ShipPhysics sailPhysics(World world, FlowField wind) {
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
    ShipRuntime runtime =
        new ShipRuntime() {
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
        };
    return new ShipPhysicsImpl(
        new PhysicsEngine(),
        world,
        config,
        new BukkitLikeResolver(),
        runtime,
        s -> 0,
        DensityField.uniform(1.2),
        wind);
  }

  private static java.util.List<ShipInspection.ForceLine> sailLines(ShipInspection report) {
    java.util.List<ShipInspection.ForceLine> sails = new java.util.ArrayList<>();
    for (ShipInspection.ForceLine line : report.forces()) {
      if (line.name().startsWith("Sail")) {
        sails.add(line);
      }
    }
    return sails;
  }

  private static java.util.Map<String, ShipInspection.ForceLine> forcesByName(
      ShipPhysics physics, Vehicle ship) {
    java.util.Map<String, ShipInspection.ForceLine> byName = new java.util.HashMap<>();
    for (ShipInspection.ForceLine line : physics.inspect(ship).forces()) {
      byName.put(line.name(), line);
    }
    return byName;
  }

  private static ShipConfig pluginScaleConfig() {
    return new ShipConfig(
        2048,
        8,
        Set.of(),
        Set.of(),
        true,
        1,
        0.5,
        16.0,
        10.0,
        10.0,
        0.5,
        0.9,
        Map.of(OAK_PLANKS, 6.0, SailShipTemplate.MAST, 7.0, SailShipTemplate.SAIL, 1.0),
        10.0,
        80.0,
        16.0,
        1e-6,
        1e-3);
  }

  private static World pluginOcean(double waterTop) {
    return new World() {
      public Vector3d gravity() {
        return new Vector3d(0, -10, 0);
      }

      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3dc p) {
            return p.y() < waterTop;
          }

          public double density(Vector3dc p) {
            return 10.0;
          }
        };
      }

      public double timeStep() {
        return 0.05;
      }
    };
  }

  private static ShipPhysics pluginPhysics(World world, ShipConfig config, RiderCount riders) {
    return new ShipPhysicsImpl(
        new PhysicsEngine(),
        world,
        config,
        new BukkitLikeResolver(),
        new ShipRuntime() {
          public void spawn(Vehicle s) {}

          public void move(Vehicle s, double oldY, double newY) {}

          public void remove(Vehicle s) {}

          public void removeAll(java.util.Collection<Vehicle> s) {}
        },
        riders,
        DensityField.uniform(1.2),
        FlowField.still());
  }

  static final class BukkitLikeResolver implements MaterialKeyResolver {
    public String key(ShipBlock block) {
      return block.blockData();
    }
  }

  static final class EngineKeyResolver implements MaterialKeyResolver {
    public String key(ShipBlock block) {
      String data = block.blockData();
      int bracket = data.indexOf('[');
      return (bracket < 0 ? data : data.substring(0, bracket)).toLowerCase();
    }
  }
}
