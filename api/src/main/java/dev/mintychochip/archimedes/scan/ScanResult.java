package dev.mintychochip.archimedes.scan;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.List;

/** Bounded result of a ship assembly scan. */
public final class ScanResult {
  /** Whether the whole component fit within the limit. */
  private final boolean complete;

  /** Scan seed x coordinate. */
  private final int rootX;

  /** Scan seed y coordinate. */
  private final int rootY;

  /** Scan seed z coordinate. */
  private final int rootZ;

  /** Captured relative positions, or null when incomplete. */
  private final List<BlockPos> captured;

  /**
   * Creates a successful scan result.
   *
   * @param rootX the scan seed x coordinate
   * @param rootY the scan seed y coordinate
   * @param rootZ the scan seed z coordinate
   * @param captured the captured relative positions
   */
  public ScanResult(int rootX, int rootY, int rootZ, List<BlockPos> captured) {
    this.complete = true;
    this.rootX = rootX;
    this.rootY = rootY;
    this.rootZ = rootZ;
    this.captured = captured == null ? null : List.copyOf(captured);
  }

  /** Creates a failed scan result with no captured blocks. */
  public ScanResult() {
    this.complete = false;
    this.rootX = 0;
    this.rootY = 0;
    this.rootZ = 0;
    this.captured = null;
  }

  /**
   * @return true when the whole component fit within the limit
   */
  public boolean complete() {
    return complete;
  }

  /**
   * @return the scan seed x coordinate
   */
  public int rootX() {
    return rootX;
  }

  /**
   * @return the scan seed y coordinate
   */
  public int rootY() {
    return rootY;
  }

  /**
   * @return the scan seed z coordinate
   */
  public int rootZ() {
    return rootZ;
  }

  /**
   * @return captured relative positions, or null when incomplete
   */
  public List<BlockPos> captured() {
    return captured;
  }
}
