package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.GravityForce;
import dev.mintychochip.phys.MediumThrustForce;
import dev.mintychochip.phys.QuadraticDragForce;
import dev.mintychochip.phys.VegetationDragForce;
import dev.mintychochip.phys.World;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.joml.Vector3d;

/**
 * Builds a physics body from a vehicle's captured hull for one integration step.
 *
 * <p>Every captured block remains a collider. Actuator flags only gate sail and engine forces.
 * Envelope lift is attached from envelope cells only, never from the whole hull volume.
 */
public final class VehicleFactory {
  /** Maps captured blocks onto configured material keys. */
  private final MaterialKeyResolver resolver;

  /** Density table, engine and envelope sets, and engine thrust. */
  private final ShipConfig config;

  /**
   * Creates a factory for one material table and resolver.
   *
   * @param resolver block material-key resolver
   * @param config density, engine, and envelope configuration
   */
  public VehicleFactory(MaterialKeyResolver resolver, ShipConfig config) {
    this.resolver = Objects.requireNonNull(resolver);
    this.config = Objects.requireNonNull(config);
  }

  /**
   * Builds a world-positioned body for one physics step.
   *
   * <p>When buoyancy is disabled, colliders and mass are still produced but gravity, lift, drag,
   * and actuators are omitted. All captured blocks remain colliders even when sails or engines are
   * off.
   *
   * @param vehicle source vehicle
   * @param world world supplying liquid for water drag and engine medium
   * @param riders rider count contributing player mass
   * @param air air density for envelope lift, sails, and dry-engine thrust
   * @param wind flow field sampled by sails
   * @param withActuators whether sail and engine forces may be attached
   * @return a body at the vehicle origin and pose
   */
  public Body buildBody(
      Vehicle vehicle,
      World world,
      int riders,
      DensityField air,
      FlowField wind,
      boolean withActuators) {
    List<Force> forces = new ArrayList<>();
    if (vehicle.buoyancyEnabled()) {
      forces.add(new GravityForce());
      forces.add(new ShipBuoyancyForce());
      forces.add(new VegetationDragForce(0.8));
      forces.add(new QuadraticDragForce(0.8, DensityField.liquid(world.fluidField())));
      double envelopeVolume = 0;
      for (ShipBlock block : vehicle.blocks()) {
        if (config.envelopeMaterials().contains(resolver.key(block))) {
          envelopeVolume += 1.0;
        }
      }
      if (envelopeVolume > 0) {
        forces.add(new EnvelopeBuoyancyForce(envelopeVolume, air));
      }
      if (withActuators && vehicle.sailsEnabled()) {
        forces.add(new QuadraticDragForce(0.05));
        forces.addAll(ShipSails.forces(vehicle, resolver, clothKeys(vehicle), air, wind));
      }
      if (withActuators && vehicle.enginesEnabled()) {
        DensityField medium =
            point ->
                world.fluidField().isFluid(point)
                    ? world.fluidField().density(point)
                    : air.density(point);
        for (ShipBlock block : vehicle.blocks()) {
          if (!config.engineMaterials().contains(resolver.key(block))) {
            continue;
          }
          Vector3d point =
              new Vector3d(block.pos().x() + 0.5, block.pos().y() + 0.5, block.pos().z() + 0.5);
          forces.add(
              new MediumThrustForce(
                  point, ShipSails.facingNormal(block.blockData()), config.engineThrust(), medium));
        }
      }
    }
    return ShipBody.from(vehicle, resolver, config, riders, forces.toArray(Force[]::new));
  }

  private Set<String> clothKeys(Vehicle vehicle) {
    Set<String> keys = new HashSet<>();
    for (ShipBlock block : vehicle.blocks()) {
      String key = resolver.key(block);
      if (isCloth(key)) {
        keys.add(key);
      }
    }
    return keys;
  }

  private static boolean isCloth(String key) {
    return key.endsWith("_wool") || key.endsWith("_banner") || key.endsWith("_wall_banner");
  }
}
