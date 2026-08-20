package dev.mintychochip.archimedes.render;

/** Extra solid voxels (world blocks) that occlude display line of sight. */
@FunctionalInterface
public interface VoxelSolid {
  /**
   * Returns whether world cell {@code (x,y,z)} is solid.
   *
   * @param x world x
   * @param y world y
   * @param z world z
   * @return {@code true} when the cell occludes
   */
  boolean isSolid(int x, int y, int z);
}
