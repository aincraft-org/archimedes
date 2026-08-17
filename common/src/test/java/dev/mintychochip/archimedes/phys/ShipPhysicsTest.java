package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.*;
import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.*;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipPhysicsTest {
  @Test void tickRestoresPoseOnBlockedMove() {
    Ship ship = new Ship(
        UUID.randomUUID(), UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks")),
        new ShipPose(0), true);
    ShipConfig config = new ShipConfig(
        2048, 8, Set.of(), Set.of(), true, 1, 0.5, 16.0, 0.05, 1.0, 0.5, 0.9,
        Map.of("minecraft:oak_planks", 600.0), 1000.0, 80.0, 16.0, 1e-6, 1e-3);
    World world = new World() {
      public Vector3 gravity() { return new Vector3(0, -10, 0); }
      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3 p) { return true; }
          public double density(Vector3 p) { return 1000; }
        };
      }
      public double timeStep() { return 0.05; }
    };
    ShipRuntime runtime = new ShipRuntime() {
      public void spawn(Ship s) {}
      public void move(Ship s, double oldY, double newY) { throw new IllegalStateException("blocked"); }
      public void remove(Ship s) {}
      public void removeAll(java.util.Collection<Ship> s) {}
    };
    ShipPhysics physics = new ShipPhysicsImpl(
        new PhysicsEngine(), world, config, new BukkitLikeResolver(), runtime, s -> 0);
    ShipPose old = ship.pose();
    assertFalse(physics.tick(ship));
    assertEquals(old.y(), ship.pose().y(), 1e-9);
  }

  static final class BukkitLikeResolver implements MaterialKeyResolver {
    public String key(ShipBlock block) { return block.blockData(); }
  }
}
