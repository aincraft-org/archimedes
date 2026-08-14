package dev.jlo.ships.collision;

import java.util.UUID;

/** Temporary collision representation owned by one ship. */
public interface CollisionVolume {
  /**
   * @return the owning ship identifier
   */
  UUID shipId();

  /**
   * Moves the volume to an integer world anchor.
   *
   * @param x world x coordinate
   * @param y world y coordinate
   * @param z world z coordinate
   */
  void move(int x, int y, int z);

  /** Removes the volume from the world. */
  void remove();
}
