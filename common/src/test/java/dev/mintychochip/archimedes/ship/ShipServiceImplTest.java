package dev.mintychochip.archimedes.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.phys.ShipInspection;
import dev.mintychochip.archimedes.sail.SailShipTemplate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavior tests for the ship assembly service. */
class ShipServiceImplTest {
  /** Common capturable material. */
  private static final String STONE = "minecraft:stone";

  /** Tagged runtime sweep marker. */
  private static final String SWEEP = "sweep";

  private static final String REMOVE_CALL = "remove";
  private static final String RESTORE_CALL = "restore";

  /** Single-block origin key. */
  private static final String ORIGIN_KEY = "100,200,300";

  /** Recorded buoyancy clear operation. */
  private static final String CLEAR_CALL = "clear";

  /** Recorded buoyancy rise operation. */
  private static final String RISE_CALL = "rise";

  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");

  /** In-memory store, world mutator, and renderer for the service. */
  private static final class Fakes implements WorldMutator {
    final Map<UUID, Vehicle> persisted = new HashMap<>();
    final List<Vehicle> rendered = new ArrayList<>();

    final List<Vehicle> removedRuntime = new ArrayList<>();
    final Map<String, String> blocks = new HashMap<>();
    boolean restoreValid = true;

    ShipOrigin origin = new ShipOrigin(WORLD, 100, 200, 300);

    @Override
    public String blockDataAt(int x, int y, int z) {
      return blocks.getOrDefault(x + "," + y + "," + z, STONE);
    }

    @Override
    public boolean clearBlocks(Vehicle ship) {
      for (var block : ship.blocks()) {
        blocks.remove(
            (ship.origin().x() + block.pos().x())
                + ","
                + (baseY(ship) + block.pos().y())
                + ","
                + (ship.origin().z() + block.pos().z()));
      }
      return true;
    }

    @Override
    public boolean validateRestore(Vehicle ship) {
      return restoreValid;
    }

    @Override
    public boolean restoreBlocks(Vehicle ship) {
      for (var block : ship.blocks()) {
        blocks.put(
            (ship.origin().x() + block.pos().x())
                + ","
                + (baseY(ship) + block.pos().y())
                + ","
                + (ship.origin().z() + block.pos().z()),
            block.blockData());
      }
      return true;
    }

    @Override
    public String lastError() {
      return "world mutation failed";
    }

    private static int baseY(Vehicle ship) {
      return ship.origin().y() + ship.pose().anchorDy();
    }
  }

  /** Buoyancy fake recording calls. */
  private static class RecordingBuoyancy implements dev.mintychochip.archimedes.phys.ShipPhysics {
    final List<String> calls = new ArrayList<>();
    boolean riseFails;
    boolean tickResult;
    boolean sinkResult = true;

    @Override
    public boolean rise(Vehicle ship) {
      calls.add(RISE_CALL);
      return !riseFails;
    }

    @Override
    public boolean tick(Vehicle ship) {
      calls.add("tick");
      return tickResult;
    }

    @Override
    public boolean sink(Vehicle ship, int blocks) {
      calls.add("sink");
      return sinkResult;
    }

    @Override
    public void clear(Vehicle ship) {
      calls.add(CLEAR_CALL);
    }

    @Override
    public ShipInspection inspect(Vehicle ship) {
      return new ShipInspection(
          ship.id(),
          ship.blockCount(),
          0,
          0,
          0,
          ship.buoyancyEnabled(),
          true,
          ship.pose().x(),
          ship.pose().y(),
          ship.pose().z(),
          0,
          0,
          0,
          0,
          0,
          0,
          List.of(),
          0,
          0,
          0);
    }
  }

  private static ShipRuntime runtime(Fakes fakes) {
    return new ShipRuntime() {
      @Override
      public void spawn(Vehicle ship) {
        fakes.rendered.add(ship);
      }

      @Override
      public void move(Vehicle ship, double oldY, double newY) {}

      @Override
      public void remove(Vehicle ship) {
        fakes.removedRuntime.add(ship);
      }

      @Override
      public void removeAll(Collection<Vehicle> ships) {
        fakes.removedRuntime.addAll(ships);
      }
    };
  }

  private static Vehicle ship(Fakes fakes) {
    return new Vehicle(
        UUID.randomUUID(),
        OWNER,
        fakes.origin,
        List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
  }

  @Test
  void loadAllInitialSweepFailureClearsRegistryAndAttemptsFinalSweep() {
    Fakes fakes = new Fakes();
    Vehicle persisted = ship(fakes);
    List<String> sweeps = new ArrayList<>();
    ShipRuntime runtime =
        new ShipRuntime() {
          @Override
          public void spawn(Vehicle ship) {}

          @Override
          public void move(Vehicle ship, double oldY, double newY) {}

          @Override
          public void remove(Vehicle ship) {}

          @Override
          public void removeAll(Collection<Vehicle> ships) {}

          @Override
          public void removeAllTagged() {
            sweeps.add(SWEEP);
            if (sweeps.size() == 1) {
              throw new ShipRuntimeException(new IllegalStateException("initial"));
            }
          }
        };
    ShipServiceImpl service =
        new ShipServiceImpl(
            new ShipStoreLike() {
              @Override
              public Map<UUID, Vehicle> loadAll() {
                return Map.of(persisted.id(), persisted);
              }

              @Override
              public void saveAll(Map<UUID, Vehicle> ships) {}
            },
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime,
            fakes,
            new RecordingBuoyancy(),
            false,
            false,
            WORLD);

    IllegalStateException failure = assertThrows(IllegalStateException.class, service::loadAll);

    assertTrue(failure.getMessage().contains("initial-tag-sweep"));
    assertEquals(List.of(SWEEP, SWEEP), sweeps);
    assertTrue(service.all().isEmpty());
  }

  @Test
  void loadAllStoreFailureStillAttemptsTaggedCleanup() {
    Fakes fakes = new Fakes();
    List<String> sweeps = new ArrayList<>();
    ShipRuntime runtime =
        new ShipRuntime() {
          @Override
          public void spawn(Vehicle ship) {}

          @Override
          public void move(Vehicle ship, double oldY, double newY) {}

          @Override
          public void remove(Vehicle ship) {}

          @Override
          public void removeAll(Collection<Vehicle> ships) {}

          @Override
          public void removeAllTagged() {
            sweeps.add(SWEEP);
          }
        };
    ShipServiceImpl service =
        new ShipServiceImpl(
            new ShipStoreLike() {
              @Override
              public Map<UUID, Vehicle> loadAll() {
                throw new ShipRuntimeException(new IllegalStateException("store"));
              }

              @Override
              public void saveAll(Map<UUID, Vehicle> ships) {}
            },
            (x, y, z) -> List.of(),
            runtime,
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);
    IllegalStateException failure = assertThrows(IllegalStateException.class, service::loadAll);
    assertTrue(failure.getMessage().contains("store-load"));
    assertEquals(List.of(SWEEP), sweeps);
    assertTrue(service.all().isEmpty());
  }

  @Test
  void loadAllCleansUpEarlierSpawnWhenLaterSpawnFails() {
    Fakes fakes = new Fakes();
    Vehicle first =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    Vehicle second =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    java.util.LinkedHashMap<UUID, Vehicle> persisted = new java.util.LinkedHashMap<>();
    persisted.put(first.id(), first);
    persisted.put(second.id(), second);
    ShipRuntime failingRuntime =
        new ShipRuntime() {
          @Override
          public void spawn(Vehicle ship) {
            if (ship.id().equals(second.id())) {
              throw new ShipRuntimeException(new IllegalStateException("spawn failed"));
            }
          }

          @Override
          public void move(Vehicle ship, double oldY, double newY) {}

          @Override
          public void remove(Vehicle ship) {
            fakes.removedRuntime.add(ship);
          }

          @Override
          public void removeAll(Collection<Vehicle> ships) {}
        };
    ShipServiceImpl service =
        new ShipServiceImpl(
            new ShipStoreLike() {
              @Override
              public Map<UUID, Vehicle> loadAll() {
                return persisted;
              }

              @Override
              public void saveAll(Map<UUID, Vehicle> ships) {}
            },
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            failingRuntime,
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);

    IllegalStateException failure = assertThrows(IllegalStateException.class, service::loadAll);
    assertTrue(failure.getMessage().contains(second.id().toString()));
    assertEquals(1, fakes.removedRuntime.size());
    assertEquals(first.id(), fakes.removedRuntime.get(0).id());
    assertTrue(service.all().isEmpty());
  }

  @Test
  void rollbackContinuesAfterPlainRuntimeCleanupFailures() {
    ShipRuntime failingRuntime =
        new ShipRuntime() {
          @Override
          public void spawn(Vehicle ship) {}

          @Override
          public void move(Vehicle ship, double oldY, double newY) {}

          @Override
          public void remove(Vehicle ship) {
            throw new IllegalStateException(REMOVE_CALL);
          }

          @Override
          public void removeAll(Collection<Vehicle> ships) {}
        };
    RecordingBuoyancy buoyancy =
        new RecordingBuoyancy() {
          @Override
          public void clear(Vehicle ship) {
            calls.add(CLEAR_CALL);
            throw new IllegalStateException("clear");
          }
        };
    WorldMutator mutator =
        new WorldMutator() {
          @Override
          public String blockDataAt(int x, int y, int z) {
            return STONE;
          }

          @Override
          public boolean clearBlocks(Vehicle ship) {
            return true;
          }

          @Override
          public boolean restoreBlocks(Vehicle ship) {
            throw new IllegalStateException(RESTORE_CALL);
          }

          @Override
          public boolean validateRestore(Vehicle ship) {
            return true;
          }

          @Override
          public String lastError() {
            return RESTORE_CALL;
          }
        };
    ShipService service =
        new ShipServiceImpl(
            new ShipStoreLike() {
              public Map<UUID, Vehicle> loadAll() {
                return Map.of();
              }

              @Override
              public void saveAll(Map<UUID, Vehicle> ships) {
                throw new IllegalStateException("persist");
              }
            },
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            failingRuntime,
            mutator,
            buoyancy,
            true,
            true,
            WORLD);

    assertThrows(ShipRuntimeException.class, () -> service.assembleAt(OWNER, 100, 200, 300, WORLD));
    assertEquals(List.of(RISE_CALL, CLEAR_CALL), buoyancy.calls);
    assertEquals("persist", service.lastError());
  }

  @Test
  void loadAllContinuesAfterPlainRuntimeCleanupFailures() {
    Fakes fakes = new Fakes();
    Vehicle first = ship(fakes);
    Vehicle second = ship(fakes);
    Map<UUID, Vehicle> persisted = new java.util.LinkedHashMap<>();
    persisted.put(first.id(), first);
    persisted.put(second.id(), second);
    List<String> calls = new ArrayList<>();
    ShipRuntime runtime =
        new ShipRuntime() {
          @Override
          public void spawn(Vehicle ship) {
            calls.add("spawn-" + ship.id());
            if (ship.id().equals(second.id())) {
              throw new IllegalStateException("spawn");
            }
          }

          @Override
          public void move(Vehicle ship, double oldY, double newY) {}

          @Override
          public void remove(Vehicle ship) {
            calls.add(REMOVE_CALL);
            throw new IllegalStateException(REMOVE_CALL);
          }

          @Override
          public void removeAll(Collection<Vehicle> ships) {}

          @Override
          public void removeAllTagged() {
            calls.add(SWEEP);
            if (calls.contains(REMOVE_CALL)) {
              throw new IllegalStateException(SWEEP);
            }
          }
        };
    ShipService service =
        new ShipServiceImpl(
            new ShipStoreLike() {
              @Override
              public Map<UUID, Vehicle> loadAll() {
                return persisted;
              }

              @Override
              public void saveAll(Map<UUID, Vehicle> ships) {}
            },
            (x, y, z) -> List.of(),
            runtime,
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);

    assertThrows(IllegalStateException.class, service::loadAll);
    assertEquals(
        List.of(SWEEP, "spawn-" + first.id(), "spawn-" + second.id(), REMOVE_CALL, SWEEP), calls);
    assertTrue(service.all().isEmpty());
  }

  @Test
  void assemblesAndPersistsShip() {
    Fakes fakes = new Fakes();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    Vehicle result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNotNull(result);
    assertEquals(1, fakes.persisted.size());
    assertEquals(1, fakes.rendered.size());
  }

  @Test
  void persistenceRuntimeFailureRollsBackAndNormalizesError() {
    Fakes fakes = new Fakes();
    fakes.blocks.put(ORIGIN_KEY, STONE);
    ShipStoreLike store =
        new ShipStoreLike() {
          int saves;

          @Override
          public Map<UUID, Vehicle> loadAll() {
            return fakes.persisted;
          }

          @Override
          public void saveAll(Map<UUID, Vehicle> ships) {
            if (++saves == 1) {
              throw new IllegalStateException("persist failed");
            }
            fakes.persisted.clear();
            fakes.persisted.putAll(ships);
          }
        };
    ShipService service =
        new ShipServiceImpl(
            store,
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);

    assertNull(service.assembleAt(OWNER, 100, 200, 300, WORLD));
    assertTrue(fakes.persisted.isEmpty());
    assertEquals(STONE, fakes.blocks.get(ORIGIN_KEY));
    assertEquals("persist failed", service.lastError());
  }

  @Test
  void nullCauseRuntimeFailureUsesSafeErrorReason() {
    Fakes fakes = new Fakes();
    fakes.blocks.put(ORIGIN_KEY, STONE);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new ShipRuntime() {
              @Override
              public void spawn(Vehicle ship) {
                throw new ShipRuntimeException("spawn failed", null);
              }

              @Override
              public void move(Vehicle ship, double oldY, double newY) {}

              @Override
              public void remove(Vehicle ship) {
                fakes.removedRuntime.add(ship);
              }

              @Override
              public void removeAll(Collection<Vehicle> ships) {}
            },
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);

    assertNull(service.assembleAt(OWNER, 100, 200, 300, WORLD));
    assertEquals("spawn failed", service.lastError());
  }

  @Test
  void rejectsAssemblyInNonBoundWorldBeforeScanningOrMutation() {
    Fakes fakes = new Fakes();
    List<String> calls = new ArrayList<>();
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> {
              calls.add("scan");
              return List.of(new BlockPos(0, 0, 0));
            },
            runtime(fakes),
            new WorldMutator() {
              @Override
              public String blockDataAt(int x, int y, int z) {
                calls.add("blockData");
                return STONE;
              }

              @Override
              public boolean clearBlocks(Vehicle ship) {
                calls.add(CLEAR_CALL);
                return true;
              }

              @Override
              public boolean validateRestore(Vehicle ship) {
                return true;
              }

              @Override
              public boolean restoreBlocks(Vehicle ship) {
                calls.add("restore");
                return true;
              }

              @Override
              public String lastError() {
                return "mutation failed";
              }
            },
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);

    assertNull(service.assembleAt(OWNER, 100, 200, 300, UUID.randomUUID()));
    assertEquals("Ship assembly is not permitted in this world", service.lastError());
    assertTrue(calls.isEmpty());
  }

  @Test
  void rejectsAssemblyInDisabledBoundWorldBeforeScanningOrMutation() {
    Fakes fakes = new Fakes();
    List<String> calls = new ArrayList<>();
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> {
              calls.add("scan");
              return List.of(new BlockPos(0, 0, 0));
            },
            runtime(fakes),
            new WorldMutator() {
              @Override
              public String blockDataAt(int x, int y, int z) {
                calls.add("blockData");
                return STONE;
              }

              @Override
              public boolean clearBlocks(Vehicle ship) {
                calls.add(CLEAR_CALL);
                return true;
              }

              @Override
              public boolean validateRestore(Vehicle ship) {
                return true;
              }

              @Override
              public boolean restoreBlocks(Vehicle ship) {
                calls.add("restore");
                return true;
              }

              @Override
              public String lastError() {
                return "mutation failed";
              }
            },
            new RecordingBuoyancy(),
            true,
            false,
            WORLD);

    assertNull(service.assembleAt(OWNER, 100, 200, 300, WORLD));
    assertEquals("Ship assembly is disabled in this world", service.lastError());
    assertTrue(calls.isEmpty());
  }

  @Test
  void findsOwnedShipInWorld() {
    Fakes fakes = new Fakes();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    service.loadAll();
    Vehicle found = service.findOwnedInWorld(OWNER, WORLD);
    assertEquals(ship.id(), found.id());
  }

  @Test
  void killRemovesRuntimeAndPersistedWithoutRestoringBlocks() {
    Fakes fakes = new Fakes();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    RecordingBuoyancy physics = new RecordingBuoyancy();
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            physics,
            true,
            true,
            WORLD);
    service.loadAll();
    boolean ok = service.kill(ship.id(), OWNER, false);
    assertTrue(ok);
    assertTrue(fakes.persisted.isEmpty());
    assertEquals(1, fakes.removedRuntime.size());
    assertTrue(fakes.blocks.isEmpty());
    assertTrue(physics.calls.contains(CLEAR_CALL));
    assertTrue(service.all().isEmpty());
  }

  @Test
  void killRejectsNonOwnerWithoutOperator() {
    Fakes fakes = new Fakes();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    service.loadAll();
    boolean ok = service.kill(ship.id(), UUID.randomUUID(), false);
    assertFalse(ok);
    assertEquals("You do not own this ship", service.lastError());
    assertEquals(1, fakes.persisted.size());
    assertTrue(fakes.removedRuntime.isEmpty());
  }

  @Test
  void killAllRemovesEveryShipWithoutRestoring() {
    Fakes fakes = new Fakes();
    Vehicle first =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    Vehicle second =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            new ShipOrigin(WORLD, 110, 200, 310),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(first.id(), first);
    fakes.persisted.put(second.id(), second);
    RecordingBuoyancy physics = new RecordingBuoyancy();
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            physics,
            true,
            true,
            WORLD);
    service.loadAll();
    assertEquals(2, service.killAll());
    assertTrue(fakes.persisted.isEmpty());
    assertEquals(2, fakes.removedRuntime.size());
    assertTrue(fakes.blocks.isEmpty());
    assertTrue(service.all().isEmpty());
    assertEquals(2, physics.calls.stream().filter(CLEAR_CALL::equals).count());
  }

  @Test
  void disassembleRemovesRuntimeAndPersisted() {
    Fakes fakes = new Fakes();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    service.loadAll();
    boolean ok = service.disassemble(ship.id(), OWNER, false);
    assertTrue(ok);
    assertTrue(fakes.persisted.isEmpty());
    assertEquals(1, fakes.removedRuntime.size());
  }

  @Test
  void disassembleRejectsNonOwnerWithoutOperator() {
    Fakes fakes = new Fakes();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    service.loadAll();
    boolean ok = service.disassemble(ship.id(), UUID.randomUUID(), false);
    assertFalse(ok);
    assertEquals(1, fakes.persisted.size());
  }

  @Test
  void a18DisassemblyAtAuthoritativeAnchor() {
    Fakes fakes = new Fakes();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)),
            new ShipPose(3.0),
            true);
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    service.loadAll();
    boolean ok = service.disassemble(ship.id(), OWNER, false);
    assertTrue(ok);
    // restored at origin.y(200) + anchor(3) = 203
    assertEquals(STONE, fakes.blocks.get("100,203,300"));
  }

  @Test
  void assembleFailsWhenScannerRejects() {
    Fakes fakes = new Fakes();
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> null,
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    Vehicle result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNull(result);
    assertEquals(0, fakes.persisted.size());
    assertEquals(0, fakes.rendered.size());
  }

  @Test
  void assembleRollsBackWhenRenderFails() {
    Fakes fakes = new Fakes();
    fakes.blocks.put(ORIGIN_KEY, STONE);
    ShipRendererLike throwing =
        new ShipRendererLike() {
          @Override
          public void render(Vehicle s, ShipHolder holder) {
            throw new ShipRuntimeException(new IllegalStateException("no display slots"));
          }

          @Override
          public void removeRuntime(Vehicle s) {
            fakes.removedRuntime.add(s);
          }

          @Override
          public void reposition(Vehicle s, double oldY, double newY) {}
        };
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new ShipRuntime() {
              @Override
              public void spawn(Vehicle ship) {
                throwing.render(ship, ignored -> {});
              }

              @Override
              public void move(Vehicle ship, double oldY, double newY) {}

              @Override
              public void remove(Vehicle ship) {
                fakes.removedRuntime.add(ship);
              }

              @Override
              public void removeAll(Collection<Vehicle> ships) {}
            },
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    Vehicle result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNull(result);
    assertEquals(0, fakes.persisted.size());
    assertEquals(STONE, fakes.blocks.get(ORIGIN_KEY));
  }

  @Test
  void assemblePersistsRollbackWhenHolderAlreadySaved() {
    Fakes fakes = new Fakes();
    fakes.blocks.put(ORIGIN_KEY, STONE);
    ShipRendererLike holderThenThrow =
        new ShipRendererLike() {
          @Override
          public void render(Vehicle s, ShipHolder holder) {
            holder.accept(s);
            throw new ShipRuntimeException(new IllegalStateException("second phase failed"));
          }

          @Override
          public void removeRuntime(Vehicle s) {
            fakes.removedRuntime.add(s);
          }

          @Override
          public void reposition(Vehicle s, double oldY, double newY) {}
        };
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            new ShipRuntime() {
              @Override
              public void spawn(Vehicle ship) {
                holderThenThrow.render(ship, ignored -> {});
              }

              @Override
              public void move(Vehicle ship, double oldY, double newY) {}

              @Override
              public void remove(Vehicle ship) {
                fakes.removedRuntime.add(ship);
              }

              @Override
              public void removeAll(Collection<Vehicle> ships) {}
            },
            fakes,
            new RecordingBuoyancy(),
            true,
            true,
            WORLD);
    Vehicle result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNull(result);
    // The store must not retain the half-saved ship after rollback.
    assertEquals(0, fakes.persisted.size());
    assertEquals(STONE, fakes.blocks.get(ORIGIN_KEY));
  }

  @Test
  void assemblyRisesShipAfterRender() {
    Fakes fakes = new Fakes();
    RecordingBuoyancy buoyancy = new RecordingBuoyancy();
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            buoyancy,
            true,
            true,
            WORLD);
    Vehicle result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNotNull(result);
    assertEquals(List.of(RISE_CALL), buoyancy.calls);
  }

  @Test
  void assemblyRollsBackWhenBuoyancyFails() {
    Fakes fakes = new Fakes();
    fakes.blocks.put(ORIGIN_KEY, STONE);
    RecordingBuoyancy buoyancy = new RecordingBuoyancy();
    buoyancy.riseFails = true;
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            buoyancy,
            true,
            true,
            WORLD);
    Vehicle result = service.assembleAt(OWNER, 100, 200, 300, WORLD);
    assertNull(result);
    assertEquals(0, fakes.persisted.size());
    assertEquals(STONE, fakes.blocks.get(ORIGIN_KEY));
  }

  @Test
  void disassembleClearsBuoyancyState() {
    Fakes fakes = new Fakes();
    RecordingBuoyancy buoyancy = new RecordingBuoyancy();
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            OWNER,
            fakes.origin,
            List.of(new ShipBlock(new BlockPos(0, 0, 0), STONE)));
    fakes.persisted.put(ship.id(), ship);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(new BlockPos(0, 0, 0)),
            runtime(fakes),
            fakes,
            buoyancy,
            true,
            true,
            WORLD);
    service.loadAll();
    service.disassemble(ship.id(), OWNER, false);
    assertTrue(buoyancy.calls.contains(CLEAR_CALL));
  }

  @Test
  void tickPersistsExactlyOnceOnlyWhenAnyShipMoves() {
    Fakes fakes = new Fakes();
    Vehicle first = ship(fakes);
    Vehicle second = ship(fakes);
    fakes.persisted.put(first.id(), first);
    fakes.persisted.put(second.id(), second);
    RecordingBuoyancy buoyancy = new RecordingBuoyancy();
    CountingStore store = new CountingStore(fakes);
    ShipServiceImpl service =
        new ShipServiceImpl(
            store, (x, y, z) -> List.of(), runtime(fakes), fakes, buoyancy, true, true, WORLD);
    service.loadAll();
    store.saves = 0;
    service.tick();
    assertEquals(0, store.saves);
    buoyancy.tickResult = true;
    service.tick();
    assertEquals(1, store.saves);
  }

  @Test
  void togglePersistsOnce() {
    Fakes fakes = new Fakes();
    Vehicle ship = ship(fakes);
    fakes.persisted.put(ship.id(), ship);
    CountingStore store = new CountingStore(fakes);
    ShipServiceImpl service =
        new ShipServiceImpl(
            store,
            (x, y, z) -> List.of(),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);
    service.loadAll();
    store.saves = 0;
    assertTrue(service.toggleBuoyancy(OWNER, WORLD));
    assertEquals(1, store.saves);
  }

  @Test
  void sinkPersistsOnceOnSuccessAndNotOnFailure() {
    Fakes fakes = new Fakes();
    Vehicle ship = ship(fakes);
    fakes.persisted.put(ship.id(), ship);
    RecordingBuoyancy buoyancy = new RecordingBuoyancy();
    CountingStore store = new CountingStore(fakes);
    ShipServiceImpl service =
        new ShipServiceImpl(
            store, (x, y, z) -> List.of(), runtime(fakes), fakes, buoyancy, true, true, WORLD);
    service.loadAll();
    store.saves = 0;
    assertTrue(service.sink(OWNER, WORLD, 1));
    assertEquals(1, store.saves);
    buoyancy.sinkResult = false;
    assertFalse(service.sink(OWNER, WORLD, 1));
    assertEquals(1, store.saves);
  }

  @Test
  void sinkRejectsNonPositiveBlocksWithoutCallingBuoyancyOrPersistence() {
    Fakes fakes = new Fakes();
    Vehicle ship = ship(fakes);
    fakes.persisted.put(ship.id(), ship);
    RecordingBuoyancy buoyancy = new RecordingBuoyancy();
    CountingStore store = new CountingStore(fakes);
    ShipServiceImpl service =
        new ShipServiceImpl(
            store, (x, y, z) -> List.of(), runtime(fakes), fakes, buoyancy, true, true, WORLD);
    service.loadAll();
    store.saves = 0;

    assertFalse(service.sink(OWNER, WORLD, 0));
    assertFalse(service.sink(OWNER, WORLD, -1));
    assertEquals(0, store.saves);
    assertFalse(buoyancy.calls.contains("sink"));
  }

  @Test
  void spawnSailRegistersTemplateWithoutClearingWorldBlocks() {
    Fakes fakes = new Fakes();
    fakes.blocks.put("5,64,8", STONE);
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);

    Vehicle ship = service.spawnSail(OWNER, WORLD, 5, 64, 8);

    assertNotNull(ship);
    assertEquals(SailShipTemplate.blocks().size(), ship.blockCount());
    assertEquals(5, ship.origin().x());
    assertEquals(64, ship.origin().y());
    assertEquals(8, ship.origin().z());
    assertEquals(OWNER, ship.ownerId());
    assertEquals(1, fakes.rendered.size());
    assertEquals(STONE, fakes.blocks.get("5,64,8"));
    assertEquals(1, service.all().size());
    long wool = ship.blocks().stream().filter(block -> block.blockData().endsWith("_wool")).count();
    assertEquals(25, wool);
  }

  @Test
  void spawnSailUsesTheNamedSizeAndRejectsUnknownNames() {
    Fakes fakes = new Fakes();
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);

    Vehicle large = service.spawnSail(OWNER, WORLD, 5, 64, 8, "large");
    assertNotNull(large);
    assertEquals(SailShipTemplate.blocks(SailShipTemplate.Size.LARGE).size(), large.blockCount());

    assertNull(service.spawnSail(OWNER, WORLD, 5, 64, 8, "huge"));
    assertEquals("Unknown sail size: huge", service.lastError());
  }

  @Test
  void spawnSailRejectsForeignAndDisabledWorlds() {
    Fakes fakes = new Fakes();
    ShipService disabled =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            false,
            false,
            WORLD);
    assertNull(disabled.spawnSail(OWNER, WORLD, 0, 64, 0));
    assertTrue(disabled.lastError().contains("disabled"));

    ShipService foreign =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(),
            runtime(fakes),
            fakes,
            new RecordingBuoyancy(),
            false,
            true,
            WORLD);
    assertNull(foreign.spawnSail(OWNER, UUID.randomUUID(), 0, 64, 0));
    assertTrue(foreign.lastError().contains("not permitted"));
    assertTrue(fakes.rendered.isEmpty());
  }

  @Test
  void spawnSailKeepsTheShipWhenRiseDoesNotMoveIt() {
    Fakes fakes = new Fakes();
    RecordingBuoyancy buoyancy = new RecordingBuoyancy();
    buoyancy.riseFails = true;
    ShipService service =
        new ShipServiceImpl(
            new MemoryStore(fakes),
            (x, y, z) -> List.of(),
            runtime(fakes),
            fakes,
            buoyancy,
            true,
            true,
            WORLD);

    Vehicle ship = service.spawnSail(OWNER, WORLD, 5, 64, 8);

    assertNotNull(ship);
    assertEquals(1, service.all().size());
    assertEquals(List.of(RISE_CALL), buoyancy.calls);
    assertEquals(1, fakes.rendered.size());
  }

  private static final class CountingStore implements ShipStoreLike {
    private final Fakes fakes;
    int saves;

    CountingStore(Fakes fakes) {
      this.fakes = fakes;
    }

    public Map<UUID, Vehicle> loadAll() {
      return fakes.persisted;
    }

    public void saveAll(Map<UUID, Vehicle> ships) {
      saves++;
      fakes.persisted.clear();
      fakes.persisted.putAll(ships);
    }
  }

  private record MemoryStore(Fakes fakes) implements ShipStoreLike {
    @Override
    public Map<UUID, Vehicle> loadAll() {
      return fakes.persisted;
    }

    @Override
    public void saveAll(Map<UUID, Vehicle> ships) {
      fakes.persisted.clear();
      fakes.persisted.putAll(ships);
    }
  }

  private record RecordingRenderer(Fakes fakes) implements ShipRendererLike {
    @Override
    public void render(Vehicle ship, ShipHolder holder) {
      fakes.rendered.add(ship);
      holder.accept(ship);
    }

    @Override
    public void removeRuntime(Vehicle ship) {
      fakes.removedRuntime.add(ship);
    }

    @Override
    public void reposition(Vehicle ship, double oldY, double newY) {}
  }
}
