package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.config.ShipConfig;
import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.phys.Body;
import dev.mintychochip.phys.Bounds;
import dev.mintychochip.phys.Collider;
import dev.mintychochip.phys.Quaternion;
import dev.mintychochip.phys.Transform;
import dev.mintychochip.phys.Vector3;
import dev.mintychochip.phys.World;

public final class WaterlineResolver {
  public static final int NO_WATER = Integer.MIN_VALUE;
  private WaterlineResolver() {}

  public static int submergedVolume(Body body, World world) {
    int count = 0;
    for (Collider c : body.colliders()) {
      Bounds b = c.shape().bounds(transform(body, c));
      int bottom = (int) Math.floor(b.min().y());
      Vector3 center = new Vector3((b.min().x() + b.max().x()) / 2.0,
                                   (b.min().y() + b.max().y()) / 2.0,
                                   (b.min().z() + b.max().z()) / 2.0);
      int ax = (int) Math.floor(center.x());
      int az = (int) Math.floor(center.z());
      int surface = columnWaterSurface(world, ax, bottom, az);
      if (surface != NO_WATER && bottom <= surface) count++;
    }
    return count;
  }

  public static boolean isPathClear(Ship ship, World world, double poseY, ShipConfig config) {
    int min = Math.min(ship.pose().anchorDy(), (int) Math.floor(poseY));
    int max = Math.max(ship.pose().anchorDy(), (int) Math.floor(poseY));
    for (int y = min; y <= max; y++) {
      for (ShipBlock block : ship.blocks()) {
        int wx = ship.origin().x() + block.pos().x();
        int wy = ship.origin().y() + y + block.pos().y();
        int wz = ship.origin().z() + block.pos().z();
        Vector3 center = new Vector3(wx + 0.5, wy + 0.5, wz + 0.5);
        if (world.isObstacle(center) && !world.fluidField().isFluid(center)) return false;
      }
    }
    return true;
  }

  private static Transform transform(Body body, Collider c) {
    return new Transform(
        body.transform().position().add(c.localTransform().position()),
        c.localTransform().orientation());
  }

  private static int columnWaterSurface(World world, int x, int bottom, int z) {
    boolean sealed = false;
    int highest = NO_WATER;
    for (int y = bottom + 64; y >= bottom - 64; y--) {
      Vector3 p = new Vector3(x + 0.5, y + 0.5, z + 0.5);
      if (world.fluidField().isFluid(p)) {
        if (!sealed && highest == NO_WATER) highest = y;
      } else if (world.isObstacle(p)) {
        sealed = true;
      }
    }
    return highest;
  }
}
