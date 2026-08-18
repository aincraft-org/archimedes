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
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class ShipSailsTest {
  private static final String OAK_PLANKS = "minecraft:oak_planks";
  private static final String WHITE_WOOL = "minecraft:white_wool";

  @Test
  void hullBlocksAreIgnored() {
    Ship ship = ship(new ShipBlock(new BlockPos(0, 0, 0), OAK_PLANKS));

    List<Force> sails =
        ShipSails.forces(
            ship,
            block -> OAK_PLANKS,
            Set.of(WHITE_WOOL),
            DensityField.uniform(1.2),
            FlowField.still());

    assertTrue(sails.isEmpty());
  }

  @Test
  void eachSailBlockBecomesAForceAtItsCenter() {
    Ship ship =
        ship(
            new ShipBlock(new BlockPos(1, 2, 3), WHITE_WOOL),
            new ShipBlock(new BlockPos(0, 0, 0), OAK_PLANKS));
    MaterialKeyResolver resolver =
        block -> block.blockData().contains("wool") ? WHITE_WOOL : OAK_PLANKS;

    List<Force> sails =
        ShipSails.forces(
            ship,
            resolver,
            Set.of(WHITE_WOOL),
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
  void woolWithoutFacingDefaultsToPlusZ() {
    Ship ship = ship(new ShipBlock(new BlockPos(0, 0, 0), WHITE_WOOL));
    List<Force> sails =
        ShipSails.forces(
            ship,
            block -> WHITE_WOOL,
            Set.of(WHITE_WOOL),
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));
    BodyImpl probe =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    Force.Result result = sails.get(0).apply(probe, world());

    assertTrue(result.force().z() > 0);
    assertEquals(0.0, result.force().x(), 1e-9);
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
    Ship ship = ship(new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL));
    List<Force> sails =
        ShipSails.forces(
            ship,
            block -> WHITE_WOOL,
            Set.of(WHITE_WOOL),
            DensityField.uniform(1.2),
            FlowField.uniform(new Vector3d(0, 0, 10)));
    Body body = new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 2, List.of(), sails);
    World world = world();

    new PhysicsEngine().step(world, List.of(body));

    assertTrue(body.linearVelocity().z() > 0);
  }

  @Test
  void largerClothProducesProportionallyMoreSailForce() {
    MaterialKeyResolver resolver = block -> WHITE_WOOL;
    DensityField air = DensityField.uniform(1.2);
    FlowField wind = FlowField.uniform(new Vector3d(0, 0, 10));
    Ship one = ship(new ShipBlock(new BlockPos(0, 0, 0), WHITE_WOOL));
    Ship four =
        ship(
            new ShipBlock(new BlockPos(0, 0, 0), WHITE_WOOL),
            new ShipBlock(new BlockPos(1, 0, 0), WHITE_WOOL),
            new ShipBlock(new BlockPos(0, 1, 0), WHITE_WOOL),
            new ShipBlock(new BlockPos(1, 1, 0), WHITE_WOOL));
    BodyImpl probe =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    World world = world();
    double oneZ =
        ShipSails.forces(one, resolver, Set.of(WHITE_WOOL), air, wind)
            .get(0)
            .apply(probe, world)
            .force()
            .z();
    double fourZ = 0;
    for (Force force : ShipSails.forces(four, resolver, Set.of(WHITE_WOOL), air, wind)) {
      fourZ += force.apply(probe, world).force().z();
    }
    assertTrue(oneZ > 0);
    assertEquals(4.0, fourZ / oneZ, 1e-9);
  }

  private static World world() {
    return new World() {
      public Vector3dc gravity() {
        return new Vector3d(0, -10, 0);
      }

      public FluidField fluidField() {
        return new FluidField() {
          public boolean isFluid(Vector3dc point) {
            return false;
          }

          public double density(Vector3dc point) {
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
