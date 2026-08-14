package dev.jlo.ships.deck;

import dev.jlo.ships.model.Ship;
import java.util.HashSet;
import java.util.Set;

/**
 * Deploys and removes solid barrier support blocks over exposed ship tops.
 * Deployment is all-or-nothing: any obstructed cell fails the whole ship
 * before any barrier is placed.
 */
public final class DeckManager {
  private final DeckSurface world;

  /** Creates a manager bound to a deck surface. */
  public DeckManager(DeckSurface world) {
    this.world = world;
  }

  /** Deploys supports for every exposed top; returns false if any is blocked. */
  public boolean deploy(Ship ship) {
    Set<long[]> supports = DeckSurface.supportPositions(ship);
    Set<long[]> placed = new HashSet<>();
    for (long[] position : supports) {
      int x = (int) position[0];
      int y = (int) position[1];
      int z = (int) position[2];
      if (!world.canPlace(x, y, z) || !world.isClear(x, y, z)) {
        for (long[] already : placed) {
          world.removeBarrier((int) already[0], (int) already[1], (int) already[2]);
        }
        return false;
      }
      if (!world.placeBarrier(x, y, z)) {
        for (long[] already : placed) {
          world.removeBarrier((int) already[0], (int) already[1], (int) already[2]);
        }
        return false;
      }
      placed.add(position);
    }
    return true;
  }

  /** Removes every deployed support for the ship. */
  public void remove(Ship ship) {
    Set<long[]> supports = DeckSurface.supportPositions(ship);
    for (long[] position : supports) {
      world.removeBarrier((int) position[0], (int) position[1], (int) position[2]);
    }
  }

  /** Removes a single support position. */
  public void removeAt(int x, int y, int z) {
    world.removeBarrier(x, y, z);
  }
}