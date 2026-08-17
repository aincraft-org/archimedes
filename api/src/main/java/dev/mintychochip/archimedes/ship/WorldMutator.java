package dev.mintychochip.archimedes.ship;

import dev.mintychochip.archimedes.model.Ship;

/**
 * Mutates the world during assembly and disassembly. Implementations must be robust against partial
 * failure and report errors through {@link #lastError()}.
 */
public interface WorldMutator {
  /**
   * Returns the serialized block data at an absolute position.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return the serialized block data
   */
  String blockDataAt(int x, int y, int z);

  /**
   * Removes every source block of a ship, replacing with air.
   *
   * @param ship the ship to clear
   * @return false when any removal fails, leaving the world unchanged
   */
  boolean clearBlocks(Ship ship);

  /**
   * Validates that every ship destination is currently empty.
   *
   * @param ship the ship to validate
   * @return true when every destination is empty
   */
  boolean validateRestore(Ship ship);

  /**
   * Restores every ship block's original data at its destination.
   *
   * @param ship the ship to restore
   * @return false when a destination became occupied mid-operation
   */
  boolean restoreBlocks(Ship ship);

  /**
   * @return the last failure message
   */
  String lastError();
}
