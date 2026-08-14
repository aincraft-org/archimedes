package dev.jlo.ships.bukkit;

import dev.jlo.ships.model.Ship;
import dev.jlo.ships.ship.WorldMutator;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Bukkit-backed world mutator: reads exact block data, clears ship source
 * blocks, and restores original data. Restore validates every destination
 * before touching anything.
 */
public final class BukkitWorldMutator implements WorldMutator {
  private final World world;
  private String lastError;

  /** Creates a mutator bound to a world. */
  public BukkitWorldMutator(World world) {
    this.world = world;
  }

  @Override
  public String blockDataAt(int x, int y, int z) {
    BlockData data = world.getBlockAt(x, y, z).getBlockData();
    return data.getAsString();
  }

  @Override
  public boolean clearBlocks(Ship ship) {
    for (var block : ship.blocks()) {
      int ax = ship.origin().x() + block.pos().x();
      int ay = ship.origin().y() + block.pos().y();
      int az = ship.origin().z() + block.pos().z();
      world.getBlockAt(ax, ay, az).setType(org.bukkit.Material.AIR, false);
    }
    return true;
  }

  @Override
  public boolean validateRestore(Ship ship) {
    for (var block : ship.blocks()) {
      int ax = ship.origin().x() + block.pos().x();
      int ay = ship.origin().y() + block.pos().y();
      int az = ship.origin().z() + block.pos().z();
      Block current = world.getBlockAt(ax, ay, az);
      if (!current.getType().isAir()) {
        lastError = "Restore blocked at " + ax + "," + ay + "," + az + ": " + current.getType();
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean restoreBlocks(Ship ship) {
    for (var block : ship.blocks()) {
      int ax = ship.origin().x() + block.pos().x();
      int ay = ship.origin().y() + block.pos().y();
      int az = ship.origin().z() + block.pos().z();
      world.getBlockAt(ax, ay, az).setBlockData(org.bukkit.Bukkit.createBlockData(block.blockData()), false);
    }
    return true;
  }

  @Override
  public String lastError() {
    return lastError;
  }
}