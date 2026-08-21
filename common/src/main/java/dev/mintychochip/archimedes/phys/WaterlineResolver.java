package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipPose;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Bounds;
import dev.mintychochip.phys.Collider;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.World;
import org.joml.Vector3d;

/** Resolves submerged unit volumes and collision-safe vertical paths. */
public final class WaterlineResolver {
  /** Sentinel for columns without water. */
  public static final int NO_WATER = Integer.MIN_VALUE;

  private WaterlineResolver() {}

  /**
   * Counts submerged colliders by sampling each collider's center column.
   *
   * <p>The water search scans a 64-block window above and below the collider and treats an obstacle
   * as sealing the column, so enclosed water does not count as a surface. One unit is counted per
   * submerged collider (not the collider's exact volume).
   *
   * @param body body whose colliders are sampled
   * @param world world supplying fluid and obstacle queries
   * @return number of submerged colliders
   */
  public static int submergedVolume(Body body, World world) {
    int count = 0;
    for (Collider c : body.colliders()) {
      if (submerged(body, c, world) != NO_WATER) {
        count++;
      }
    }
    return count;
  }

  /**
   * Displaced fluid mass and mass-weighted wet-cell centroid.
   *
   * @param mass displaced mass in the same units as {@link World#fluidField()} density
   * @param centroid world-space center of buoyancy; unused when {@code mass} is 0
   */
  public record Displacement(double mass, Vector3d centroid) {}

  /**
   * Sums fluid density of each collider times the fraction of that cell below the free surface.
   *
   * <p>The free surface is the top of the highest water block ({@code surface + 1}). A deck that
   * only kisses the water therefore lifts by a sliver, not a full cell.
   *
   * <p>Density is sampled at the water column, not the body origin, so a deck that sits in water
   * still lifts when the origin corner is in air.
   *
   * @param body body whose colliders are sampled
   * @param world world supplying fluid
   * @return displaced mass in the same units as {@link World#fluidField()} density
   */
  public static double displacedMass(Body body, World world) {
    return displacement(body, world).mass();
  }

  /**
   * Displaced fluid mass and mass-weighted wet-cell centroid for {@code body}.
   *
   * <p>Each wet collider contributes at the center of its submerged slab {@code (midX, minY +
   * wetHeight/2, midZ)}, weighted by wet fraction times sampled density.
   *
   * @param body body whose colliders are sampled
   * @param world world supplying fluid
   * @return displaced mass and centroid; centroid is unused when mass is 0
   */
  public static Displacement displacement(Body body, World world) {
    double mass = 0;
    Vector3d moment = new Vector3d();
    for (Collider c : body.colliders()) {
      Bounds b = c.shape().bounds(transform(body, c));
      int surface = waterSurface(world, b);
      if (surface == NO_WATER) {
        continue;
      }
      double height = b.max().y() - b.min().y();
      if (height <= 0) {
        continue;
      }
      double waterTop = surface + 1.0;
      double wetHeight = Math.min(b.max().y(), waterTop) - b.min().y();
      if (wetHeight <= 0) {
        continue;
      }
      double fraction = Math.min(1.0, wetHeight / height);
      double midX = (b.min().x() + b.max().x()) / 2.0;
      double midZ = (b.min().z() + b.max().z()) / 2.0;
      Vector3d wet = new Vector3d(midX, surface + 0.5, midZ);
      double dm = fraction * world.fluidField().density(wet);
      if (dm <= 0) {
        continue;
      }
      mass += dm;
      moment.fma(dm, new Vector3d(midX, b.min().y() + wetHeight / 2.0, midZ));
    }
    if (mass == 0) {
      return new Displacement(0, new Vector3d());
    }
    return new Displacement(mass, moment.div(mass));
  }

  /**
   * @param body body supplying the world transform
   * @param collider hull cell
   * @param world fluid and obstacle map
   * @return column surface Y when this cell is submerged, otherwise {@link #NO_WATER}
   */
  private static int submerged(Body body, Collider collider, World world) {
    Bounds b = collider.shape().bounds(transform(body, collider));
    int surface = waterSurface(world, b);
    if (surface == NO_WATER || b.min().y() >= surface + 1.0) {
      return NO_WATER;
    }
    return surface;
  }

  /**
   * @param world fluid map
   * @param bounds collider world bounds
   * @return highest water-block Y in the collider's column, or {@link #NO_WATER}
   */
  private static int waterSurface(World world, Bounds b) {
    int bottom = (int) Math.floor(b.min().y());
    double midX = (b.min().x() + b.max().x()) / 2.0;
    double midZ = (b.min().z() + b.max().z()) / 2.0;
    return columnWaterSurface(world, (int) Math.floor(midX), bottom, (int) Math.floor(midZ));
  }

  /**
   * Checks every integer-Y pose between the current and target positions.
   *
   * <p>The path check is all-or-nothing: any solid non-fluid block rejects the move.
   *
   * @param ship ship whose blocks define the swept volume
   * @param world world supplying obstacle and fluid queries
   * @param poseY target relative Y pose
   * @param config ship movement configuration; reserved for future movement tolerances and
   *     currently unused
   * @return whether every sampled block path position is clear
   */
  public static boolean isPathClear(Vehicle ship, World world, double poseY, ShipConfig config) {
    return isPathClear(ship, world, new ShipPose(ship.pose().x(), poseY, ship.pose().z()), config);
  }

  /**
   * Checks every integer cell between the current pose and {@code target}.
   *
   * @param ship ship whose blocks define the swept volume
   * @param world world supplying obstacle and fluid queries
   * @param target destination pose
   * @param config unused; reserved for future tolerances
   * @return whether every sampled cell is clear
   */
  public static boolean isPathClear(Vehicle ship, World world, ShipPose target, ShipConfig config) {
    return isPathClear(ship, world, target, config, false);
  }

  /**
   * Like {@link #isPathClear(Vehicle, World, ShipPose, ShipConfig)} but solids at or below the keel
   * are ignored. Used for XZ slides so ground contact cannot freeze the hull.
   *
   * @param ship ship whose blocks define the swept volume
   * @param world world supplying obstacle and fluid queries
   * @param target destination pose
   * @param config unused; reserved for future tolerances
   * @return whether the slide is clear of walls
   */
  public static boolean isHorizontalPathClear(
      Vehicle ship, World world, ShipPose target, ShipConfig config) {
    return isPathClear(ship, world, target, config, true);
  }

  private static boolean isPathClear(
      Vehicle ship, World world, ShipPose target, ShipConfig config, boolean ignoreKeel) {
    int minX = Math.min(ship.pose().anchorDx(), target.anchorDx());
    int maxX = Math.max(ship.pose().anchorDx(), target.anchorDx());
    int minY = Math.min(ship.pose().anchorDy(), target.anchorDy());
    int maxY = Math.max(ship.pose().anchorDy(), target.anchorDy());
    int minZ = Math.min(ship.pose().anchorDz(), target.anchorDz());
    int maxZ = Math.max(ship.pose().anchorDz(), target.anchorDz());
    int keel = ship.origin().y() + ship.pose().anchorDy() + minRelativeY(ship);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          for (ShipBlock block : ship.blocks()) {
            int wx = ship.origin().x() + x + block.pos().x();
            int wy = ship.origin().y() + y + block.pos().y();
            int wz = ship.origin().z() + z + block.pos().z();
            if (ignoreKeel && wy <= keel) {
              continue;
            }
            Vector3d center = new Vector3d(wx + 0.5, wy + 0.5, wz + 0.5);
            if (world.isObstacle(center) && !world.fluidField().isFluid(center)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * @param ship hull whose lowest relative Y is needed
   * @return minimum block y, or 0 when empty
   */
  private static int minRelativeY(Vehicle ship) {
    int min = Integer.MAX_VALUE;
    for (ShipBlock block : ship.blocks()) {
      min = Math.min(min, block.pos().y());
    }
    return min == Integer.MAX_VALUE ? 0 : min;
  }

  /**
   * Builds a collider transform by composing the body's world pose with the local collider pose.
   *
   * @param body body supplying the world transform
   * @param c collider supplying the local transform
   * @return world-space collider transform
   */
  private static Transform transform(Body body, Collider c) {
    return body.transform().compose(c.localTransform());
  }

  /**
   * Finds the highest fluid sample in the bounded column scan. An obstacle encountered below an
   * already-found fluid block does not erase that surface, but it prevents lower (deeper) fluid
   * blocks from being considered, so only the shallowest unsealed water counts.
   *
   * @param world world supplying fluid and obstacle queries
   * @param x column x coordinate
   * @param bottom lower scan anchor
   * @param z column z coordinate
   * @return highest fluid sample, or {@link #NO_WATER} when none is found
   */
  private static int columnWaterSurface(World world, int x, int bottom, int z) {
    boolean sealed = false;
    int highest = NO_WATER;
    for (int y = bottom + 64; y >= bottom - 64; y--) {
      Vector3d p = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
      if (world.fluidField().isFluid(p)) {
        if (!sealed && highest == NO_WATER) highest = y;
      } else if (world.isObstacle(p)) {
        sealed = true;
      }
    }
    return highest;
  }
}
