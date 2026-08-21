package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShipMassModelTest {
  @Test
  void massIncludesMaterialBlocksAndRiders() {
    Vehicle ship =
        new Vehicle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
            List.of(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks")),
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
            Map.of("minecraft:oak_planks", 600.0),
            1000.0,
            80.0,
            16.0,
            1e-6,
            1e-3);
    MaterialKeyResolver resolver = block -> block.blockData();
    assertEquals(600, ShipMassModel.mass(ship, resolver, config, 0), 1e-9);
    assertEquals(760, ShipMassModel.mass(ship, resolver, config, 2), 1e-9);
  }

  @Test
  void riderCountCannotBeNegative() {
    assertThrows(IllegalArgumentException.class, () -> new SimpleRiderCount(-1));
  }

  private record SimpleRiderCount(int count) implements RiderCount {
    public SimpleRiderCount {
      if (count < 0) {
        throw new IllegalArgumentException("negative riders");
      }
    }

    @Override
    public int count(Vehicle ship) {
      return count;
    }
  }
}
