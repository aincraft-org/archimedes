package dev.jlo.ships.ship;

import dev.jlo.ships.model.Ship;

/**
 * Mutates the world during assembly and disassembly. Implementations must be
 * robust against partial failure and report errors through {@link #lastError()}.
 */
public interface WorldMutator {
  /** Returns the serialized block data at an absolute position. */
  String blockDataAt(int x, int y, int z);

  /**
   * Removes every source block of a ship (replacing with air) after
   * validation. Returns false when any removal fails, leaving the world
   * unchanged.
   */
  boolean clearBlocks(Ship ship);

  /** Validates that every ship destination is currently empty or air. */
  boolean validateRestore(Ship ship);

  /**
   * Restores every ship block's original data at its destination, returning
   * false when a destination became occupied mid-operation and rolling back
   * already-placed blocks.
   */
  boolean restoreBlocks(Ship ship);

  /** Returns the last failure message. */
  String lastError();
}