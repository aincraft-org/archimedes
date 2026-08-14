package dev.jlo.ships.deck;

import dev.jlo.ships.model.Ship;
import java.util.HashSet;
import java.util.Set;

/**
 * Deploys and removes solid barrier support blocks over exposed ship tops. Deployment is
 * all-or-nothing: any obstructed cell fails the whole ship before any barrier is placed.
 */
public class DeckManager {
  /** The deck surface used for placement and removal. */
  private final DeckSurface world;

  /**
   * Creates a manager bound to a deck surface.
   *
   * @param world the deck surface
   */
  public DeckManager(DeckSurface world) {
    this.world = world;
  }

  /**
   * Deploys supports for every exposed top.
   *
   * @param ship the ship to support
   * @return false if any support cell is obstructed
   */
  public boolean deploy(Ship ship) {
    Set<long[]> supports = DeckSurface.supportPositions(ship);
    Set<long[]> placed = new HashSet<>();
    for (long[] position : supports) {
      int x = (int) position[0];
      int y = (int) position[1];
      int z = (int) position[2];
      if (!world.canPlace(x, y, z) || !world.isClear(x, y, z)) {
        removeAll(placed);
        return false;
      }
      if (!world.placeBarrier(x, y, z)) {
        removeAll(placed);
        return false;
      }
      placed.add(position);
    }
    return true;
  }

  /**
   * Removes every deployed support for the ship.
   *
   * @param ship the ship whose supports to remove
   */
  public void remove(Ship ship) {
    Set<long[]> supports = DeckSurface.supportPositions(ship);
    for (long[] position : supports) {
      world.removeBarrier((int) position[0], (int) position[1], (int) position[2]);
    }
  }

  /**
   * Removes a single support position.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   */
  public void removeAt(int x, int y, int z) {
    world.removeBarrier(x, y, z);
  }

  /**
   * @return the last deployment failure message
   */
  public String lastError() {
    return "deck supports are obstructed";
  }

  private void removeAll(Set<long[]> placed) {
    for (long[] already : placed) {
      world.removeBarrier((int) already[0], (int) already[1], (int) already[2]);
    }
  }
}
