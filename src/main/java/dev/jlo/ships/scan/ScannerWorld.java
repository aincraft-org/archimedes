package dev.jlo.ships.scan;

import java.util.Set;
import org.bukkit.World;

/** World reads the ship scanner needs, separated for unit testing. */
public interface ScannerWorld {
  /** Returns the material registry name at a position. */
  String materialAt(int x, int y, int z);

  /** Returns whether the block at a position is air. */
  boolean airAt(int x, int y, int z);

  /** Wraps a Bukkit world. */
  static ScannerWorld of(World world) {
    return new ScannerWorld() {
      @Override
      public String materialAt(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().getKey().toString();
      }

      @Override
      public boolean airAt(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isAir();
      }
    };
  }
}