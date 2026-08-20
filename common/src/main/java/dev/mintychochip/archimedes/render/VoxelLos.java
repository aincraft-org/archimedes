package dev.mintychochip.archimedes.render;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.Set;

/**
 * Voxel DDA line of sight. Occupied ship cells and world solids on the ray occlude; the target cell
 * itself does not.
 */
public final class VoxelLos {
  private VoxelLos() {}

  /**
   * Returns whether the ray from the eye to the center of {@code target} is free of occluders.
   *
   * @param occupied ship cells in the same integer space as {@code target}
   * @param worldSolids extra solid world cells
   * @param eyeX eye x
   * @param eyeY eye y
   * @param eyeZ eye z
   * @param target display cell
   * @return {@code true} when no occupied or world-solid cell lies on the ray before the target
   */
  public static boolean hasLineOfSight(
      Set<BlockPos> occupied,
      Set<BlockPos> worldSolids,
      double eyeX,
      double eyeY,
      double eyeZ,
      BlockPos target) {
    int x = floor(eyeX);
    int y = floor(eyeY);
    int z = floor(eyeZ);
    if (x == target.x() && y == target.y() && z == target.z()) {
      return true;
    }
    double destX = target.x() + 0.5;
    double destY = target.y() + 0.5;
    double destZ = target.z() + 0.5;
    double dx = destX - eyeX;
    double dy = destY - eyeY;
    double dz = destZ - eyeZ;
    int stepX = step(dx);
    int stepY = step(dy);
    int stepZ = step(dz);
    double tMaxX = tMax(eyeX, dx, x, stepX);
    double tMaxY = tMax(eyeY, dy, y, stepY);
    double tMaxZ = tMax(eyeZ, dz, z, stepZ);
    double tDeltaX = tDelta(dx);
    double tDeltaY = tDelta(dy);
    double tDeltaZ = tDelta(dz);
    boolean first = true;
    for (int i = 0; i < 512; i++) {
      if (!first && occludes(occupied, worldSolids, x, y, z, target)) {
        return false;
      }
      if (x == target.x() && y == target.y() && z == target.z()) {
        return true;
      }
      first = false;
      if (tMaxX < tMaxY) {
        if (tMaxX < tMaxZ) {
          if (tMaxX > 1.0) {
            break;
          }
          x += stepX;
          tMaxX += tDeltaX;
        } else {
          if (tMaxZ > 1.0) {
            break;
          }
          z += stepZ;
          tMaxZ += tDeltaZ;
        }
      } else if (tMaxY < tMaxZ) {
        if (tMaxY > 1.0) {
          break;
        }
        y += stepY;
        tMaxY += tDeltaY;
      } else {
        if (tMaxZ > 1.0) {
          break;
        }
        z += stepZ;
        tMaxZ += tDeltaZ;
      }
    }
    return x == target.x() && y == target.y() && z == target.z();
  }

  private static boolean occludes(
      Set<BlockPos> occupied, Set<BlockPos> worldSolids, int x, int y, int z, BlockPos target) {
    if (x == target.x() && y == target.y() && z == target.z()) {
      return false;
    }
    BlockPos cell = new BlockPos(x, y, z);
    return occupied.contains(cell) || worldSolids.contains(cell);
  }

  private static int step(double delta) {
    if (delta > 0) {
      return 1;
    }
    if (delta < 0) {
      return -1;
    }
    return 0;
  }

  private static double tDelta(double delta) {
    if (delta == 0) {
      return Double.POSITIVE_INFINITY;
    }
    return Math.abs(1.0 / delta);
  }

  private static double tMax(double start, double delta, int voxel, int step) {
    if (step == 0) {
      return Double.POSITIVE_INFINITY;
    }
    if (step > 0) {
      return (voxel + 1.0 - start) / delta;
    }
    return (start - voxel) / -delta;
  }

  private static int floor(double value) {
    int i = (int) value;
    return value < i ? i - 1 : i;
  }
}
