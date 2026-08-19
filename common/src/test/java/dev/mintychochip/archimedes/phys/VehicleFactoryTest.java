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
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.GravityForce;
import dev.mintychochip.phys.MediumThrustForce;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.PressureSailForce;
import dev.mintychochip.phys.World;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class VehicleFactoryTest {
  private static final String OAK_PLANKS = "minecraft:oak_planks";
  private static final String WHITE_WOOL = "minecraft:white_wool";
  private static final String FURNACE = "minecraft:furnace";
  private static final String SLIME_BLOCK = "minecraft:slime_block";
  private static final MaterialKeyResolver RESOLVER =
      block -> {
        String data = block.blockData();
        int bracket = data.indexOf('[');
        return (bracket < 0 ? data : data.substring(0, bracket)).toLowerCase();
      };

  @Test
  void stackedDeckBlocksAddMass() {
    Vehicle small = vehicle(List.of(oak(0, 0, 0)));
    Vehicle stacked = vehicle(List.of(oak(0, 0, 0), oak(0, 1, 0)));
    VehicleFactory factory = factory();
    assertTrue(
        factory.buildBody(stacked, world(), 0, air(), wind(), true).mass()
            > factory.buildBody(small, world(), 0, air(), wind(), true).mass());
  }

  @Test
  void furledSailsKeepMassAndDropSailForce() {
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0), wool(0, 2, 0)));
    vehicle.setSailsEnabled(false);
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    assertEquals(2, body.colliders().size());
    assertFalse(body.forces().stream().anyMatch(PressureSailForce.class::isInstance));
    assertTrue(body.forces().stream().anyMatch(GravityForce.class::isInstance));
  }

  @Test
  void enginesOffKeepMassAndDropThrust() {
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0), furnace(1, 0, 0)));
    vehicle.setEnginesEnabled(false);
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    assertEquals(2, body.colliders().size());
    assertFalse(body.forces().stream().anyMatch(MediumThrustForce.class::isInstance));
  }

  @Test
  void envelopeCellsHoverInEmptyAir() {
    // One slime: ρV = 1.2 > mass 1, so aerostatic lift exceeds weight.
    Vehicle vehicle = vehicle(List.of(slime(0, 0, 0)));
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    assertTrue(body.forces().stream().anyMatch(EnvelopeBuoyancyForce.class::isInstance));
    new PhysicsEngine().step(world(), List.of(body));
    assertTrue(body.linearVelocity().y() > 0);
  }

  @Test
  void hullWithoutEnvelopeDoesNotAttachEnvelopeLift() {
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0)));
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    assertFalse(body.forces().stream().anyMatch(EnvelopeBuoyancyForce.class::isInstance));
  }

  @Test
  void oakPlusEnvelopeDoesNotCountOakAsGasVolume() {
    EnvelopeBuoyancyForce oneCell = new EnvelopeBuoyancyForce(1.0, air());
    Vehicle vehicle = vehicle(List.of(oak(0, 0, 0), slime(0, 3, 0)));
    Body body = factory().buildBody(vehicle, world(), 0, air(), wind(), true);
    EnvelopeBuoyancyForce attached =
        (EnvelopeBuoyancyForce)
            body.forces().stream()
                .filter(EnvelopeBuoyancyForce.class::isInstance)
                .findFirst()
                .orElseThrow();
    Force.Result expected = oneCell.apply(body, world());
    Force.Result actual = attached.apply(body, world());
    assertEquals(expected.force().y(), actual.force().y(), 1e-9);
  }

  private static Vehicle vehicle(List<ShipBlock> blocks) {
    return new Vehicle(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 64, 0),
        blocks,
        new ShipPose(0),
        true);
  }

  private static ShipBlock oak(int x, int y, int z) {
    return new ShipBlock(new BlockPos(x, y, z), OAK_PLANKS);
  }

  private static ShipBlock wool(int x, int y, int z) {
    return new ShipBlock(new BlockPos(x, y, z), WHITE_WOOL + "[facing=south]");
  }

  private static ShipBlock furnace(int x, int y, int z) {
    return new ShipBlock(new BlockPos(x, y, z), FURNACE + "[facing=south]");
  }

  private static ShipBlock slime(int x, int y, int z) {
    return new ShipBlock(new BlockPos(x, y, z), SLIME_BLOCK);
  }

  private static VehicleFactory factory() {
    return new VehicleFactory(RESOLVER, config());
  }

  private static ShipConfig config() {
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
        Map.of(OAK_PLANKS, 6.0, WHITE_WOOL, 1.0, FURNACE, 8.0, SLIME_BLOCK, 1.0),
        10.0,
        80.0,
        16.0,
        1e-6,
        1e-3);
  }

  private static DensityField air() {
    return DensityField.uniform(1.2);
  }

  private static FlowField wind() {
    return FlowField.uniform(new Vector3d(0, 0, 8));
  }

  private static World world() {
    return new EnvelopeBuoyancyForceTest.UpWorld();
  }
}
