package dev.jlo.ships.bukkit;

import dev.jlo.ships.model.BlockPos;
import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipOrigin;
import dev.jlo.ships.scan.ScanResult;
import dev.jlo.ships.scan.ScannerWorld;
import dev.jlo.ships.scan.Seed;
import dev.jlo.ships.scan.ShipScanner;
import java.util.List;
import java.util.Set;
import org.bukkit.World;

/** Runs the bounded component scan against a Bukkit world. */
public final class BukkitScannerWorld implements dev.jlo.ships.ship.ComponentScanner {
  private final World world;
  private final int maximumBlocks;
  private final Set<String> forbidden;
  private final ScannerWorld scannerWorld;

  /** Creates the scanner bound to a world and configuration. */
  public BukkitScannerWorld(World world, int maximumBlocks, Set<String> forbidden) {
    this.world = world;
    this.maximumBlocks = maximumBlocks;
    this.forbidden = forbidden;
    this.scannerWorld = ScannerWorld.of(world);
  }

  @Override
  public List<BlockPos> scan(int x, int y, int z) {
    ScanResult result = ShipScanner.scan(scannerWorld, new Seed(x, y, z), maximumBlocks, forbidden);
    return result.complete() ? result.captured() : null;
  }
}