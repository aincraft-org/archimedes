package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipPose;
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
      Bounds b = c.shape().bounds(transform(body, c));
      int bottom = (int) Math.floor(b.min().y());
      Vector3d center =
          new Vector3d(
              (b.min().x() + b.max().x()) / 2.0,
              (b.min().y() + b.max().y()) / 2.0,
              (b.min().z() + b.max().z()) / 2.0);
      int ax = (int) Math.floor(center.x());
      int az = (int) Math.floor(center.z());
      int surface = columnWaterSurface(world, ax, bottom, az);
      if (surface != NO_WATER && bottom <= surface) count++;
    }
    return count;
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
  public static boolean isPathClear(Ship ship, World world, double poseY, ShipConfig config) {
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
  public static boolean isPathClear(Ship ship, World world, ShipPose target, ShipConfig config) {
    int minX = Math.min(ship.pose().anchorDx(), target.anchorDx());
    int maxX = Math.max(ship.pose().anchorDx(), target.anchorDx());
    int minY = Math.min(ship.pose().anchorDy(), target.anchorDy());
    int maxY = Math.max(ship.pose().anchorDy(), target.anchorDy());
    int minZ = Math.min(ship.pose().anchorDz(), target.anchorDz());
    int maxZ = Math.max(ship.pose().anchorDz(), target.anchorDz());
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          for (ShipBlock block : ship.blocks()) {
            int wx = ship.origin().x() + x + block.pos().x();
            int wy = ship.origin().y() + y + block.pos().y();
            int wz = ship.origin().z() + z + block.pos().z();
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
   * Builds a collider transform by combining the body's world position with local collider pose.
   *
   * @param body body supplying the world transform
   * @param c collider supplying the local transform
   * @return world-space collider transform
   */
  private static Transform transform(Body body, Collider c) {
    return new Transform(
        body.transform().position().add(c.localTransform().position(), new Vector3d()),
        c.localTransform().orientation());
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
