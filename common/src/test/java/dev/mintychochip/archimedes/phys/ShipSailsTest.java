package dev.mintychochip.archimedes.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipOrigin;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.BodyImpl;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.FluidField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.PhysicsEngine;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.World;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ShipSailsTest {
  @Test
  void hullBlocksAreIgnored() {
    Ship ship = ship(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks"));

    List<Force> sails =
        ShipSails.forces(
            ship,
            block -> "minecraft:oak_planks",
            Set.of("minecraft:white_wool"),
            DensityField.uniform(1.2),
            FlowField.still());

    assertTrue(sails.isEmpty());
  }

  @Test
  void eachSailBlockBecomesAForceAtItsCenter() {
    Ship ship =
        ship(
            new ShipBlock(new BlockPos(1, 2, 3), "minecraft:white_wool"),
            new ShipBlock(new BlockPos(0, 0, 0), "minecraft:oak_planks"));
    MaterialKeyResolver resolver =
        block ->
            block.blockData().contains("wool") ? "minecraft:white_wool" : "minecraft:oak_planks";

    List<Force> sails =
        ShipSails.forces(
            ship,
            resolver,
            Set.of("minecraft:white_wool"),
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));

    assertEquals(1, sails.size());
    BodyImpl probe =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = world();
    assertEquals(0.0, sails.get(0).apply(probe, world).force().x(), 1e-9);
    assertTrue(sails.get(0).apply(probe, world).force().z() > 0);
  }

  @Test
  void blockFacingSetsTheClothNormal() {
    Ship ship =
        ship(new ShipBlock(new BlockPos(0, 0, 0), "minecraft:white_wall_banner[facing=west]"));
    List<Force> sails =
        ShipSails.forces(
            ship,
            block -> "minecraft:white_wall_banner",
            Set.of("minecraft:white_wall_banner"),
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(-10, 0, 0)));

    BodyImpl probe =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = world();
    Force.Result result = sails.get(0).apply(probe, world);

    assertTrue(result.force().x() < 0);
    assertEquals(0.0, result.force().z(), 1e-9);
  }

  @Test
  void structureSailsDriveABodyThroughTheEngine() {
    Ship ship = ship(new ShipBlock(new BlockPos(0, 1, 0), "minecraft:white_wool"));
    List<Force> sails =
        ShipSails.forces(
            ship,
            block -> "minecraft:white_wool",
            Set.of("minecraft:white_wool"),
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));
    Body body = new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), sails);
    World world = world();

    new PhysicsEngine().step(world, List.of(body));

    assertTrue(body.linearVelocity().z() > 0);
  }

  private static World world() {
    return new World() {
      public org.joml.Vector3dc gravity() {
        return new Vector3d(0, -10, 0);
      }

      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(org.joml.Vector3dc point) {
            return false;
          }

          public double density(org.joml.Vector3dc point) {
            return 0;
          }
        };
      }

      public double timeStep() {
        return 0.1;
      }
    };
  }

  private static Ship ship(ShipBlock... blocks) {
    return new Ship(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new ShipOrigin(UUID.randomUUID(), 0, 0, 0),
        List.of(blocks));
  }
}
