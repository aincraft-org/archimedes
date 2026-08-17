package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.ship.WorldMutator;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Bukkit-backed world mutator: reads exact block data, clears ship source blocks, and restores
 * original data. Restore validates every destination before touching anything.
 */
public final class BukkitWorldMutator implements WorldMutator {
  /** The Bukkit world being mutated. */
  private final World world;

  /** Last failure message. */
  private String lastError;

  /**
   * Creates a mutator bound to a world.
   *
   * @param world the Bukkit world
   */
  public BukkitWorldMutator(World world) {
    this.world = world;
  }

  private static int baseY(Ship ship) {
    return ship.origin().y() + ship.pose().anchorDy();
  }

  /**
   * Returns the serialized block data at an absolute position.
   *
   * @param x the x coordinate
   * @param y the y coordinate
   * @param z the z coordinate
   * @return the serialized block data
   */
  @Override
  public String blockDataAt(int x, int y, int z) {
    return world.getBlockAt(x, y, z).getBlockData().getAsString();
  }

  /**
   * Removes every source block of the ship, replacing with air.
   *
   * @param ship the ship to clear
   * @return true
   */
  @Override
  public boolean clearBlocks(Ship ship) {
    for (var block : ship.blocks()) {
      int ax = ship.origin().x() + block.pos().x();
      int ay = baseY(ship) + block.pos().y();
      int az = ship.origin().z() + block.pos().z();
      world.getBlockAt(ax, ay, az).setType(Material.AIR, false);
    }
    return true;
  }

  /**
   * Validates that every ship destination is currently empty.
   *
   * @param ship the ship to validate
   * @return true when every destination is empty
   */
  @Override
  public boolean validateRestore(Ship ship) {
    for (var block : ship.blocks()) {
      int ax = ship.origin().x() + block.pos().x();
      int ay = baseY(ship) + block.pos().y();
      int az = ship.origin().z() + block.pos().z();
      Block current = world.getBlockAt(ax, ay, az);
      if (!current.getType().isAir()) {
        lastError = "Restore blocked at " + ax + "," + ay + "," + az + ": " + current.getType();
        return false;
      }
    }
    return true;
  }

  /**
   * Restores every ship block's original data at its destination.
   *
   * @param ship the ship to restore
   * @return true
   */
  @Override
  public boolean restoreBlocks(Ship ship) {
    for (var block : ship.blocks()) {
      int ax = ship.origin().x() + block.pos().x();
      int ay = baseY(ship) + block.pos().y();
      int az = ship.origin().z() + block.pos().z();
      world
          .getBlockAt(ax, ay, az)
          .setBlockData(org.bukkit.Bukkit.createBlockData(block.blockData()), false);
    }
    return true;
  }

  /**
   * @return the last failure message
   */
  @Override
  public String lastError() {
    return lastError;
  }
}
