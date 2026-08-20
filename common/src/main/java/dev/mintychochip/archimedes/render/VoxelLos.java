package dev.mintychochip.archimedes.render;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.Set;

/**
 * Voxel DDA line of sight. A cell is visible when the eye is inside it, or when any exposed face
 * that faces the viewer has a clear ray. Occupied ship cells and world solids occlude; the target
 * cell itself does not. Sampling the facing face (not the cell center) keeps coplanar hull from
 * hiding its own surface.
 */
public final class VoxelLos {
  /** Pushes the sample point into the air just outside an exposed face. */
  private static final double FACE_OUTSET = 1.0e-3;

  private VoxelLos() {}

  /**
   * Returns whether {@code target} is visible from the eye.
   *
   * @param occupied ship cells in the same integer space as {@code target}
   * @param worldSolids extra solid world cells
   * @param eyeX eye x
   * @param eyeY eye y
   * @param eyeZ eye z
   * @param target display cell
   * @return {@code true} when an exposed facing face is unoccluded
   */
  public static boolean hasLineOfSight(
      Set<BlockPos> occupied,
      Set<BlockPos> worldSolids,
      double eyeX,
      double eyeY,
      double eyeZ,
      BlockPos target) {
    return hasLineOfSight(
        occupied,
        (x, y, z) -> worldSolids.contains(new BlockPos(x, y, z)),
        eyeX,
        eyeY,
        eyeZ,
        target);
  }

  /**
   * Returns whether {@code target} is visible from the eye through occupancy and extra solids.
   *
   * @param occupied ship cells
   * @param extra additional solid probe (world blocks)
   * @param eyeX eye x
   * @param eyeY eye y
   * @param eyeZ eye z
   * @param target display cell
   * @return {@code true} when the target is unoccluded
   */
  public static boolean hasLineOfSight(
      Set<BlockPos> occupied,
      VoxelSolid extra,
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
    return facingFaceClear(occupied, extra, eyeX, eyeY, eyeZ, target, -1, 0, 0)
        || facingFaceClear(occupied, extra, eyeX, eyeY, eyeZ, target, 1, 0, 0)
        || facingFaceClear(occupied, extra, eyeX, eyeY, eyeZ, target, 0, -1, 0)
        || facingFaceClear(occupied, extra, eyeX, eyeY, eyeZ, target, 0, 1, 0)
        || facingFaceClear(occupied, extra, eyeX, eyeY, eyeZ, target, 0, 0, -1)
        || facingFaceClear(occupied, extra, eyeX, eyeY, eyeZ, target, 0, 0, 1);
  }

  /**
   * Returns whether the exposed face along {@code (dx, dy, dz)} faces the eye and the air just
   * outside it is reachable.
   *
   * @param occupied ship cells
   * @param extra world solids
   * @param eyeX eye x
   * @param eyeY eye y
   * @param eyeZ eye z
   * @param target display cell
   * @param dx face normal x
   * @param dy face normal y
   * @param dz face normal z
   * @return {@code true} when this facing face is visible
   */
  private static boolean facingFaceClear(
      Set<BlockPos> occupied,
      VoxelSolid extra,
      double eyeX,
      double eyeY,
      double eyeZ,
      BlockPos target,
      int dx,
      int dy,
      int dz) {
    if (occupied.contains(new BlockPos(target.x() + dx, target.y() + dy, target.z() + dz))) {
      return false;
    }
    double faceX = target.x() + 0.5 + dx * 0.5;
    double faceY = target.y() + 0.5 + dy * 0.5;
    double faceZ = target.z() + 0.5 + dz * 0.5;
    double toward = (eyeX - faceX) * dx + (eyeY - faceY) * dy + (eyeZ - faceZ) * dz;
    if (toward <= 0) {
      return false;
    }
    return segmentClear(
        occupied,
        extra,
        eyeX,
        eyeY,
        eyeZ,
        faceX + dx * FACE_OUTSET,
        faceY + dy * FACE_OUTSET,
        faceZ + dz * FACE_OUTSET,
        target);
  }

  /**
   * Returns whether the segment from the eye to {@code (destX, destY, destZ)} is free of occluders.
   *
   * @param occupied ship cells
   * @param extra world solids
   * @param eyeX eye x
   * @param eyeY eye y
   * @param eyeZ eye z
   * @param destX sample x
   * @param destY sample y
   * @param destZ sample z
   * @param target display cell that does not occlude
   * @return {@code true} when the sample is reached
   */
  private static boolean segmentClear(
      Set<BlockPos> occupied,
      VoxelSolid extra,
      double eyeX,
      double eyeY,
      double eyeZ,
      double destX,
      double destY,
      double destZ,
      BlockPos target) {
    int x = floor(eyeX);
    int y = floor(eyeY);
    int z = floor(eyeZ);
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
      if (!first && occludes(occupied, extra, x, y, z, target)) {
        return false;
      }
      first = false;
      if (tMaxX < tMaxY) {
        if (tMaxX < tMaxZ) {
          if (tMaxX >= 1.0) {
            return true;
          }
          x += stepX;
          tMaxX += tDeltaX;
        } else {
          if (tMaxZ >= 1.0) {
            return true;
          }
          z += stepZ;
          tMaxZ += tDeltaZ;
        }
      } else if (tMaxY < tMaxZ) {
        if (tMaxY >= 1.0) {
          return true;
        }
        y += stepY;
        tMaxY += tDeltaY;
      } else {
        if (tMaxZ >= 1.0) {
          return true;
        }
        z += stepZ;
        tMaxZ += tDeltaZ;
      }
    }
    return false;
  }

  private static boolean occludes(
      Set<BlockPos> occupied, VoxelSolid extra, int x, int y, int z, BlockPos target) {
    if (x == target.x() && y == target.y() && z == target.z()) {
      return false;
    }
    return occupied.contains(new BlockPos(x, y, z)) || extra.isSolid(x, y, z);
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
