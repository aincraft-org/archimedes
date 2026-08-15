package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;

/**
 * Carries non-ship entities that are standing on a ship so they move with it.
 *
 * <p>Implementations are best-effort: they should not throw exceptions for expected Bukkit failures
 * such as an entity leaving the world or a teleport returning false.
 */
public interface ShipEntityCarrier {
  /**
   * Carries eligible entities on the ship by the same vertical delta.
   *
   * @param ship ship being moved
   * @param oldY previous pose y
   * @param newY new pose y
   */
  void carry(Ship ship, double oldY, double newY);
}
