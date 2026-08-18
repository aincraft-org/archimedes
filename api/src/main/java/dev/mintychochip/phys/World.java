package dev.mintychochip.phys;

import org.joml.Vector3dc;

/** Environmental inputs consumed while integrating physics bodies. */
public interface World {
  /**
   * Returns the world gravity vector.
   *
   * @return gravity vector
   */
  Vector3dc gravity();

  /**
   * Returns the fluid field used for environmental sampling.
   *
   * @return fluid field
   */
  FluidField fluidField();

  /**
   * Returns the integration timestep in seconds.
   *
   * @return timestep in seconds
   */
  double timeStep();

  /**
   * Reports whether a point is occupied by an obstacle.
   *
   * @param point world-space point to test
   * @return {@code false} by default when no obstacle map is supplied
   */
  default boolean isObstacle(Vector3dc point) {
    return false;
  }

  /**
   * Reports whether the chunk at the given chunk coordinates is available for sampling.
   *
   * <p>The default is {@code true} so test worlds stay fully loaded. Bukkit adapters must implement
   * this with a chunk-cache lookup ({@code World#isChunkLoaded}), never {@code getChunkAt}, which
   * loads from disk.
   *
   * @param chunkX chunk X ({@code blockX >> 4})
   * @param chunkZ chunk Z ({@code blockZ >> 4})
   * @return whether the chunk is in the loaded-chunk cache
   */
  default boolean isChunkLoaded(int chunkX, int chunkZ) {
    return true;
  }
}
