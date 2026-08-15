package dev.jlo.ships.collision;

import java.util.UUID;

/** Temporary collision representation owned by one ship. */
public interface CollisionVolume {
  /**
   * @return the owning ship identifier
   */
  UUID shipId();

  /**
   * Moves the volume to a fractional world anchor.
   *
   * @param x world x coordinate
   * @param y world y coordinate
   * @param z world z coordinate
   */
  void move(double x, double y, double z);

  /** Removes the volume from the world. */
  void remove();
}
