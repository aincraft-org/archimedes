package dev.jlo.ships.bukkit;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.scan.ScanResult;
import dev.jlo.ships.scan.ScannerWorld;
import dev.jlo.ships.scan.Seed;
import dev.jlo.ships.scan.ShipScanner;
import java.util.List;
import java.util.Set;
import org.bukkit.World;

/** Runs the bounded component scan against a Bukkit world. */
public final class BukkitScannerWorld implements dev.jlo.ships.ship.ComponentScanner {
  /** The Bukkit world being scanned. */
  private final World world;

  /** Maximum captured blocks per ship. */
  private final int maximumBlocks;

  /** Forbidden material registry names. */
  private final Set<String> forbidden;

  /** Scanner view over the world. */
  private final ScannerWorld scannerWorld;

  /**
   * Creates the scanner bound to a world and configuration.
   *
   * @param world the Bukkit world
   * @param maximumBlocks the maximum captured blocks
   * @param forbidden the forbidden material names
   */
  public BukkitScannerWorld(World world, int maximumBlocks, Set<String> forbidden) {
    this.world = world;
    this.maximumBlocks = maximumBlocks;
    this.forbidden = forbidden;
    this.scannerWorld = ScannerWorld.of(world);
  }

  /**
   * Scans the component containing the seed.
   *
   * @param x the seed x coordinate
   * @param y the seed y coordinate
   * @param z the seed z coordinate
   * @return the component relative positions, or null when invalid
   */
  @Override
  public List<BlockPos> scan(int x, int y, int z) {
    ScanResult result = ShipScanner.scan(scannerWorld, new Seed(x, y, z), maximumBlocks, forbidden);
    return result.complete() ? result.captured() : null;
  }
}
