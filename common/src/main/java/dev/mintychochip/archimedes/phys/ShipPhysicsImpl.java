package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.sail.SailMesh;
import dev.mintychochip.archimedes.ship.ShipRuntime;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.BodyImpl;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.GravityForce;
import dev.mintychochip.phys.MediumThrustForce;
import dev.mintychochip.phys.Physics;
import dev.mintychochip.phys.PressureSailForce;
import dev.mintychochip.phys.QuadraticDragForce;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.VegetationDragForce;
import dev.mintychochip.phys.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Default ship physics facade that steps attached forces and commits runtime movement.
 *
 * <p>Movement is path-checked and rolled back when the runtime cannot apply it. Linear velocity is
 * retained per ship between ticks and cleared when movement is explicitly reset.
 */
public final class ShipPhysicsImpl implements ShipPhysics {
  /** Generic stateless integrator. */
  private final Physics physics;

  /** Physics world adapter. */
  private final World world;

  /** Vehicle physics configuration. */
  private final ShipConfig config;

  /** Block material key resolver. */
  private final MaterialKeyResolver resolver;

  /** Runtime movement coordinator. */
  private final ShipRuntime runtime;

  /** Rider count provider. */
  private final RiderCount riderCount;

  /** Air density used by sails and air drag. */
  private final DensityField air;

  /** Wind sampled by structure sails. */
  private final FlowField wind;

  /** Per-ship retained linear velocity. */
  private final Map<UUID, Vector3d> velocities = new HashMap<>();

  /** Per-ship last tick duration in nanoseconds. */
  private final Map<UUID, Long> lastTickNanos = new HashMap<>();

  /** Torn cloth ragdolls. */
  private final List<ClothDebris> debris = new ArrayList<>();

  /**
   * Creates a ship physics facade with still air (no sail drive).
   *
   * @param physics generic physics integrator
   * @param world physics world
   * @param config ship configuration
   * @param resolver block material resolver
   * @param runtime runtime movement coordinator
   * @param riderCount rider count provider
   */
  public ShipPhysicsImpl(
      Physics physics,
      World world,
      ShipConfig config,
      MaterialKeyResolver resolver,
      ShipRuntime runtime,
      RiderCount riderCount) {
    this(
        physics,
        world,
        config,
        resolver,
        runtime,
        riderCount,
        DensityField.uniform(1.2),
        FlowField.still());
  }

  /**
   * Creates a ship physics facade with an explicit atmosphere and wind.
   *
   * @param physics generic physics integrator
   * @param world physics world
   * @param config ship configuration
   * @param resolver block material resolver
   * @param runtime runtime movement coordinator
   * @param riderCount rider count provider
   * @param air air density for sails
   * @param wind flow field for sails
   */
  public ShipPhysicsImpl(
      Physics physics,
      World world,
      ShipConfig config,
      MaterialKeyResolver resolver,
      ShipRuntime runtime,
      RiderCount riderCount,
      DensityField air,
      FlowField wind) {
    this.physics = physics;
    this.world = world;
    this.config = config;
    this.resolver = resolver;
    this.runtime = runtime;
    this.riderCount = riderCount;
    this.air = air;
    this.wind = wind;
  }

  /**
   * Advances one physics tick, including sails when cloth is present.
   *
   * @param ship ship to update
   * @return whether the ship moved
   */
  @Override
  public boolean tick(Vehicle ship) {
    if (!ship.buoyancyEnabled()) return false;
    if (!chunksLoaded(ship)) return false;
    long started = System.nanoTime();
    boolean moved = integrate(ship, 1, true);
    stepDebris();
    lastTickNanos.put(ship.id(), System.nanoTime() - started);
    return moved;
  }

  /**
   * Steps the engine until vertical motion settles, without sail drive.
   *
   * @param ship ship to raise
   * @return whether the request succeeded
   */
  @Override
  public boolean rise(Vehicle ship) {
    if (!ship.buoyancyEnabled()) return true;
    if (!chunksLoaded(ship)) return false;
    Body probe = body(ship, false);
    if (WaterlineResolver.submergedVolume(probe, world) == 0) return false;
    velocities.put(ship.id(), new Vector3d());
    return integrate(ship, 80, false);
  }

  /**
   * Moves directly downward by the requested number of blocks, clamped to the fall limit.
   *
   * @param ship ship to sink
   * @param blocks positive downward distance
   * @return whether the path was clear and movement succeeded
   */
  @Override
  public boolean sink(Vehicle ship, int blocks) {
    if (!ship.buoyancyEnabled() || blocks <= 0) return false;
    if (!chunksLoaded(ship)) return false;
    ShipPose old = ship.pose();
    double targetY = Math.max(-config.maxFall(), old.y() - blocks);
    return moveDirect(ship, old, new ShipPose(old.x(), targetY, old.z()));
  }

  /**
   * Removes the ship's retained velocity.
   *
   * @param ship ship whose transient physics state is removed
   */
  @Override
  public void clear(Vehicle ship) {
    velocities.remove(ship.id());
    lastTickNanos.remove(ship.id());
  }

  /**
   * Samples attached forces and ship factors without committing a move.
   *
   * @param ship ship to inspect
   * @return diagnostic snapshot
   */
  @Override
  public ShipInspection inspect(Vehicle ship) {
    int cloth = 0;
    for (ShipBlock block : ship.blocks()) {
      if (SailMesh.isCloth(block.blockData())) {
        cloth++;
      }
    }
    int riders = riderCount.count(ship);
    Vector3d velocity = velocities.getOrDefault(ship.id(), new Vector3d());
    long lastTick = lastTickNanos.getOrDefault(ship.id(), 0L);
    boolean loaded = chunksLoaded(ship);
    if (!loaded) {
      return new ShipInspection(
          ship.id(),
          ship.blockCount(),
          cloth,
          riders,
          ShipMassModel.mass(ship, resolver, config, riders),
          ship.buoyancyEnabled(),
          false,
          ship.pose().x(),
          ship.pose().y(),
          ship.pose().z(),
          velocity.x(),
          velocity.y(),
          velocity.z(),
          0,
          lastTick,
          0L,
          List.of(),
          0,
          0,
          0);
    }
    long started = System.nanoTime();
    Body body = body(ship, true);
    body.setLinearVelocity(velocity);
    List<ShipInspection.ForceLine> lines = new ArrayList<>();
    Map<String, SailGroup> sails = new LinkedHashMap<>();
    double netX = 0;
    double netY = 0;
    double netZ = 0;
    for (Force force : body.forces()) {
      Force.Result result = force.apply(body, world);
      netX += result.force().x();
      netY += result.force().y();
      netZ += result.force().z();
      if (force instanceof PressureSailForce sail) {
        String facing = facingLabel(sail.localNormal());
        sails.merge(facing, SailGroup.of(result, sail.area()), SailGroup::plus);
        continue;
      }
      lines.add(
          new ShipInspection.ForceLine(
              lawName(force),
              result.force().x(),
              result.force().y(),
              result.force().z(),
              result.torque().x(),
              result.torque().y(),
              result.torque().z()));
    }
    for (Map.Entry<String, SailGroup> entry : sails.entrySet()) {
      SailGroup group = entry.getValue();
      lines.add(
          new ShipInspection.ForceLine(
              "Sail " + entry.getKey() + " " + areaLabel(group.area),
              group.fx,
              group.fy,
              group.fz,
              group.tx,
              group.ty,
              group.tz));
    }
    long sample = System.nanoTime() - started;
    return new ShipInspection(
        ship.id(),
        ship.blockCount(),
        cloth,
        riders,
        body.mass(),
        ship.buoyancyEnabled(),
        true,
        ship.pose().x(),
        ship.pose().y(),
        ship.pose().z(),
        velocity.x(),
        velocity.y(),
        velocity.z(),
        WaterlineResolver.submergedVolume(body, world),
        lastTick,
        sample,
        List.copyOf(lines),
        netX,
        netY,
        netZ);
  }

  /**
   * Maps a cloth normal onto the nearest cardinal axis label.
   *
   * @param normal body-frame cloth normal
   * @return {@code +X}, {@code -X}, {@code +Y}, {@code -Y}, {@code +Z}, or {@code -Z}
   */
  private static String facingLabel(Vector3dc normal) {
    double ax = Math.abs(normal.x());
    double ay = Math.abs(normal.y());
    double az = Math.abs(normal.z());
    if (ax >= ay && ax >= az) {
      return normal.x() >= 0 ? "+X" : "-X";
    }
    if (ay >= az) {
      return normal.y() >= 0 ? "+Y" : "-Y";
    }
    return normal.z() >= 0 ? "+Z" : "-Z";
  }

  /**
   * Formats summed sail area for inspect labels.
   *
   * @param area cloth area in square metres
   * @return compact area suffix such as {@code 25m2}
   */
  private static String areaLabel(double area) {
    if (Math.abs(area - Math.rint(area)) < 1e-9) {
      return String.format(Locale.ROOT, "%.0fm2", area);
    }
    return String.format(Locale.ROOT, "%.1fm2", area);
  }

  /** Accumulated inspect sample for one sail facing. */
  private static final class SailGroup {
    /** Force x. */
    private final double fx;

    /** Force y. */
    private final double fy;

    /** Force z. */
    private final double fz;

    /** Torque x. */
    private final double tx;

    /** Torque y. */
    private final double ty;

    /** Torque z. */
    private final double tz;

    /** Summed cloth area. */
    private final double area;

    private SailGroup(
        double fx, double fy, double fz, double tx, double ty, double tz, double area) {
      this.fx = fx;
      this.fy = fy;
      this.fz = fz;
      this.tx = tx;
      this.ty = ty;
      this.tz = tz;
      this.area = area;
    }

    /**
     * @param result sampled force and torque
     * @param area cloth area of this sail cell
     * @return a one-cell group
     */
    private static SailGroup of(Force.Result result, double area) {
      return new SailGroup(
          result.force().x(),
          result.force().y(),
          result.force().z(),
          result.torque().x(),
          result.torque().y(),
          result.torque().z(),
          area);
    }

    /**
     * @param other group facing the same way
     * @return summed force, torque, and area
     */
    private SailGroup plus(SailGroup other) {
      return new SailGroup(
          fx + other.fx,
          fy + other.fy,
          fz + other.fz,
          tx + other.tx,
          ty + other.ty,
          tz + other.tz,
          area + other.area);
    }
  }

  private static String lawName(Force force) {
    if (force instanceof PressureSailForce) {
      return "Sail";
    }
    if (force instanceof QuadraticDragForce drag) {
      return drag.densityScaled() ? "WaterDrag" : "Drag";
    }
    if (force instanceof VegetationDragForce) {
      return "Vegetation";
    }
    if (force instanceof ShipBuoyancyForce) {
      return "Buoyancy";
    }
    if (force instanceof GravityForce) {
      return "Gravity";
    }
    String name = force.getClass().getSimpleName();
    if (name.endsWith("Force")) {
      return name.substring(0, name.length() - "Force".length());
    }
    return name;
  }

  /**
   * True when every chunk occupied by a ship block is in the world's loaded-chunk cache.
   *
   * @param ship ship whose cells are mapped to chunk coordinates
   * @return whether physics sampling is safe
   */
  private boolean chunksLoaded(Vehicle ship) {
    for (ShipBlock block : ship.blocks()) {
      int worldX = ship.origin().x() + ship.pose().anchorDx() + block.pos().x();
      int worldZ = ship.origin().z() + ship.pose().anchorDz() + block.pos().z();
      if (!world.isChunkLoaded(worldX >> 4, worldZ >> 4)) {
        return false;
      }
    }
    return true;
  }

  private Body body(Vehicle ship, boolean withSails) {
    List<Force> forces = new ArrayList<>();
    forces.add(new GravityForce());
    forces.add(new ShipBuoyancyForce());
    forces.add(new VegetationDragForce(0.8));
    forces.add(new QuadraticDragForce(0.8, DensityField.liquid(world.fluidField())));
    if (withSails) {
      forces.add(new QuadraticDragForce(0.05));
      forces.addAll(ShipSails.forces(ship, resolver, clothKeys(ship), air, wind));
      if (ship.enginesEnabled()) {
        DensityField medium =
            point ->
                world.fluidField().isFluid(point)
                    ? world.fluidField().density(point)
                    : air.density(point);
        for (ShipBlock block : ship.blocks()) {
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
    return ShipBody.from(
        ship, resolver, config, riderCount.count(ship), forces.toArray(Force[]::new));
  }

  private Set<String> clothKeys(Vehicle ship) {
    Set<String> keys = new HashSet<>();
    for (ShipBlock block : ship.blocks()) {
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

  private void tearOverloadedCloth(Vehicle ship, Body body, Vector3dc incomingVelocity) {
    List<ShipBlock> failing = new ArrayList<>();
    Map<BlockPos, Double> loads = new HashMap<>();
    boolean hullPresent = SailRigging.hasRigid(ship);
    for (ShipBlock block : ship.blocks()) {
      if (ship.isTorn(block.pos()) || !SailMesh.isCloth(block.blockData())) {
        continue;
      }
      int distance = SailRigging.distanceToRigid(ship, block.pos());
      double load = clothLoad(block, incomingVelocity);
      if (!SailRigging.fails(load, distance, SailRigging.DEFAULT_BREAK_LOAD, hullPresent)) {
        continue;
      }
      failing.add(block);
      loads.put(block.pos(), load);
    }
    for (ShipBlock block : failing) {
      if (!ship.tearCloth(block.pos())) {
        continue;
      }
      Vector3d worldPos =
          new Vector3d(
              ship.origin().x() + ship.pose().x() + block.pos().x(),
              ship.origin().y() + ship.pose().y() + block.pos().y(),
              ship.origin().z() + ship.pose().z() + block.pos().z());
      Vector3d impulse =
          new Vector3d(ShipSails.facingNormal(block.blockData()))
              .mul(loads.get(block.pos()) * 0.02);
      impulse.add(body.linearVelocity());
      Vector3d spin = new Vector3d(block.pos().x() * 0.1, 2, block.pos().z() * 0.1);
      ClothDebris piece = new ClothDebris(block.blockData(), worldPos, impulse, spin);
      debris.add(piece);
      runtime.spawnClothRagdoll(
          ship, piece.id(), piece.appearance(), worldPos.x(), worldPos.y(), worldPos.z());
    }
  }

  /**
   * Aerodynamic load on a cloth cell using the gameplay pose: identity orientation and the pre-step
   * linear velocity. Post-step spin is not part of {@code ShipPose} and must not inflate apparent
   * wind.
   *
   * @param block cloth cell
   * @param linearVelocity hull velocity at the start of the tick
   * @return pressure-sail force magnitude
   */
  private double clothLoad(ShipBlock block, Vector3dc linearVelocity) {
    BodyImpl probe =
        new BodyImpl(new Transform(new Vector3d(), new Quaterniond()), 1, List.of(), List.of());
    probe.setLinearVelocity(linearVelocity);
    return new PressureSailForce(
            new Vector3d(block.pos().x() + 0.5, block.pos().y() + 0.5, block.pos().z() + 0.5),
            ShipSails.facingNormal(block.blockData()),
            1.0,
            air,
            wind)
        .apply(probe, world)
        .force()
        .length();
  }

  private void stepDebris() {
    for (ClothDebris piece : debris) {
      piece.step(world, physics);
      Quaterniond q = piece.orientation();
      runtime.moveClothRagdoll(
          piece.id(),
          piece.position().x(),
          piece.position().y(),
          piece.position().z(),
          q.x(),
          q.y(),
          q.z(),
          q.w());
    }
  }

  /**
   * Steps physics, clamps vertical travel, damps retained velocity, and commits movement.
   *
   * @param ship ship being simulated
   * @param steps number of physics steps to perform
   * @param withSails whether to attach sails and air drag
   * @return whether the ship moved
   */
  private boolean integrate(Vehicle ship, int steps, boolean withSails) {
    ShipPose old = ship.pose();
    Body body = body(ship, withSails);
    Vector3d incoming = new Vector3d(velocities.getOrDefault(ship.id(), new Vector3d()));
    body.setLinearVelocity(incoming);
    double newY = old.y();
    for (int i = 0; i < steps; i++) {
      physics.step(world, List.of(body));
      newY = clamp(ship, old.y(), body);
      if (!withSails) {
        body.setLinearVelocity(new Vector3d(body.linearVelocity()).mul(config.damping()));
        if (Math.abs(body.linearVelocity().y()) < config.draftTolerance()) {
          break;
        }
      }
    }
    velocities.put(ship.id(), new Vector3d(body.linearVelocity()).mul(config.damping()));
    if (withSails) {
      tearOverloadedCloth(ship, body, incoming);
    }
    ShipPose next =
        new ShipPose(
            body.transform().position().x() - ship.origin().x(),
            newY,
            body.transform().position().z() - ship.origin().z());
    if (Math.abs(next.x() - old.x()) < config.draftTolerance()
        && Math.abs(next.y() - old.y()) < config.draftTolerance()
        && Math.abs(next.z() - old.z()) < config.draftTolerance()) {
      return false;
    }
    return moveDirect(ship, old, next);
  }

  /**
   * Clamps a simulated absolute height to the configured rise/fall range.
   *
   * @param ship ship whose origin defines the relative height
   * @param oldY previous relative pose height
   * @param body simulated body whose position is being clamped
   * @return the clamped relative pose height
   */
  private double clamp(Vehicle ship, double oldY, Body body) {
    double rawY = body.transform().position().y() - ship.origin().y();
    double low = oldY - config.maxFall();
    double high = oldY + config.maxRise();
    boolean clamped = rawY < low || rawY > high;
    double clampedY = clamped ? Math.min(high, Math.max(low, rawY)) : rawY;
    if (clamped) {
      Vector3d velocity = new Vector3d(body.linearVelocity());
      velocity.y = 0;
      body.setLinearVelocity(velocity);
      body.setTransform(
          new Transform(
              new Vector3d(
                  body.transform().position().x(),
                  ship.origin().y() + clampedY,
                  body.transform().position().z()),
              body.transform().orientation()));
    }
    return clampedY;
  }

  /**
   * Commits a path-checked pose and rolls back pose and velocity on runtime failure.
   *
   * <p>When the full pose is blocked but the same XZ with the old Y is clear, the vertical part is
   * dropped so a seafloor or ground contact cannot cancel sail drive.
   *
   * @param ship ship being moved
   * @param oldPose previous pose
   * @param newPose target pose
   * @return whether the runtime move succeeded
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private boolean moveDirect(Vehicle ship, ShipPose oldPose, ShipPose newPose) {
    ShipPose target = newPose;
    if (!WaterlineResolver.isPathClear(ship, world, target, config)) {
      target = new ShipPose(newPose.x(), oldPose.y(), newPose.z());
      if (Math.abs(target.x() - oldPose.x()) < config.draftTolerance()
          && Math.abs(target.z() - oldPose.z()) < config.draftTolerance()) {
        return false;
      }
      if (!WaterlineResolver.isHorizontalPathClear(ship, world, target, config)) {
        return false;
      }
      Vector3d velocity = velocities.get(ship.id());
      if (velocity != null) {
        velocity.y = 0;
      }
    }
    try {
      ship.setPose(target);
      runtime.move(ship, oldPose, target);
      return true;
    } catch (RuntimeException failure) {
      ship.setPose(oldPose);
      velocities.put(ship.id(), new Vector3d());
      return false;
    }
  }
}
