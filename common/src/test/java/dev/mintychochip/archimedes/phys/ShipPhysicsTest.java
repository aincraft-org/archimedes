package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.sail.SailShipTemplate;
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
  void tickStillSailsWhenTheSeafloorBlocksAGravityStep() {
    Ship ship =
        new Ship(
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
              public void spawn(Ship s) {}

              public void move(Ship s, double oldY, double newY) {}

              public void remove(Ship s) {}

              public void removeAll(java.util.Collection<Ship> s) {}
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
    Ship ship =
        new Ship(
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
              public void spawn(Ship s) {}

              public void move(Ship s, double oldY, double newY) {}

              public void remove(Ship s) {}

              public void removeAll(java.util.Collection<Ship> s) {}
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
  void riseDoesNotTeleportASurfaceSailIntoTheWater() {
    Ship ship =
        new Ship(
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
              public void spawn(Ship s) {}

              public void move(Ship s, double oldY, double newY) {}

              public void remove(Ship s) {}

              public void removeAll(java.util.Collection<Ship> s) {}
            },
            s -> 0);
    physics.rise(ship);
    assertTrue(ship.pose().y() > -2.0, "rise must not slam maxFall; y=" + ship.pose().y());
    assertTrue(ship.pose().y() < 3.0, "rise must not slam maxRise; y=" + ship.pose().y());
  }

  @Test
  void drySailFallsInsteadOfGlidingUnderPluginGravity() {
    double gravity = 10.0;
    Ship ship =
        new Ship(
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
              public void spawn(Ship s) {}

              public void move(Ship s, double oldY, double newY) {}

              public void remove(Ship s) {}

              public void removeAll(java.util.Collection<Ship> s) {}
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

  @Test
  void inspectReportsMassFactorsAndSampledForces() {
    Ship ship =
        new Ship(
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
  }

  @Test
  void pluginScaleWeightExceedsRestSailForce() {
    double oakDensity = 6.0;
    double defaultDensity = 10.0;
    Ship ship =
        new Ship(
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
              public void spawn(Ship s) {}

              public void move(Ship s, double oldY, double newY) {}

              public void remove(Ship s) {}

              public void removeAll(java.util.Collection<Ship> s) {}
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
          public void spawn(Ship s) {}

          public void move(Ship s, double oldY, double newY) {}

          public void remove(Ship s) {}

          public void removeAll(java.util.Collection<Ship> s) {}
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
    Ship one =
        new Ship(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL)),
            new ShipPose(0),
            true);
    Ship four =
        new Ship(
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
    Ship ship =
        new Ship(
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
              public void spawn(Ship s) {}

              public void move(Ship s, double oldY, double newY) {}

              public void remove(Ship s) {}

              public void removeAll(java.util.Collection<Ship> s) {}
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
    Ship dryShip = clothShip();
    Ship wetShip = clothShip();
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

  private static Ship clothShip() {
    return new Ship(
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
          public void spawn(Ship s) {}

          public void move(Ship s, double oldY, double newY) {}

          public void remove(Ship s) {}

          public void removeAll(java.util.Collection<Ship> s) {}
        };
    return new ShipPhysicsImpl(
        new PhysicsEngine(),
        world,
        config,
        new BukkitLikeResolver(),
        runtime,
        s -> 0,
        DensityField.uniform(1.2),
        FlowField.uniform(new Vector3d(0, 0, 10)));
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
      ShipPhysics physics, Ship ship) {
    java.util.Map<String, ShipInspection.ForceLine> byName = new java.util.HashMap<>();
    for (ShipInspection.ForceLine line : physics.inspect(ship).forces()) {
      byName.put(line.name(), line);
    }
    return byName;
  }

  static final class BukkitLikeResolver implements MaterialKeyResolver {
    public String key(ShipBlock block) {
      return block.blockData();
    }
  }
}
