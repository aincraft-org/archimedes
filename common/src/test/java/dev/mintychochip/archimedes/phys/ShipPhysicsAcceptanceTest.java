package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ShipPhysicsAcceptanceTest {
  @Test
  void a9StaleRiderRemovedAndMoveStillSucceeds() {
    Ship ship = ship(List.of(new ShipBlock(new BlockPos(0, 0, 0), "light")));
    ShipConfig config = config(200.0);

    World world = waterWorld(20.0, 1000.0);

    AtomicInteger counter = new AtomicInteger(1);
    RiderCount riderCount = s -> counter.getAndSet(0);
    ShipRuntime runtime = recordingRuntime();

    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(), world, config, b -> b.blockData(), runtime, riderCount);

    physics.tick(ship);
    double loadedY = ship.pose().y();
    physics.tick(ship);
    assertTrue(ship.pose().y() > loadedY, "ship rises after the stale rider is dropped");
  }

  @Test
  void a16ReloadedConfigRecalculatesTarget() {
    Ship ship = ship(stackOf("medium", 5));
    World world = waterWorld(20.0, 1000.0);
    ShipRuntime runtime = recordingRuntime();

    ShipPhysics lightPhysics =
        new ShipPhysicsImpl(
            new PhysicsEngine(), world, config(800.0), b -> b.blockData(), runtime, s -> 0);
    settle(lightPhysics, ship);
    double lightY = ship.pose().y();

    ship.setPose(new ShipPose(10));
    ShipPhysics heavyPhysics =
        new ShipPhysicsImpl(
            new PhysicsEngine(), world, config(1000.0), b -> b.blockData(), runtime, s -> 0);
    settle(heavyPhysics, ship);
    double heavyY = ship.pose().y();

    assertTrue(heavyY < lightY, "higher density reload produces a deeper draft");
  }

  @Test
  void a20ClearRemovesPerShipState() {
    Ship first = ship(List.of(new ShipBlock(new BlockPos(0, 0, 0), "light")));
    Ship second = ship(List.of(new ShipBlock(new BlockPos(0, 0, 0), "light")));
    ShipConfig config = config(1000.0);
    World world = waterWorld(20.0, 1000.0);
    ShipRuntime runtime = recordingRuntime();

    ShipPhysics physics =
        new ShipPhysicsImpl(
            new PhysicsEngine(), world, config, b -> b.blockData(), runtime, s -> 0);

    physics.tick(first);
    double firstY = first.pose().y();
    physics.tick(second);
    double secondY = second.pose().y();
    assertEquals(firstY, secondY, 1e-9, "identical ships reach same pose");

    physics.clear(first);
    first.setPose(new ShipPose(10));
    physics.tick(first);
    assertEquals(
        firstY, first.pose().y(), 1e-9, "cleared ship starts from rest and matches the first step");
  }

  private static void settle(ShipPhysics physics, Ship ship) {
    for (int i = 0; i < 50; i++) {
      physics.tick(ship);
    }
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

  private static ShipConfig config(double density) {
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
        Map.of("light", density, "medium", density),
        1.0,
        1000.0,
        32.0,
        1e-6,
        1e-3);
  }

  private static ShipRuntime recordingRuntime() {
    return new ShipRuntime() {
      public void spawn(Ship s) {}

      public void move(Ship s, double oldY, double newY) {}

      public void remove(Ship s) {}

      public void removeAll(java.util.Collection<Ship> s) {}
    };
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
}
