package dev.jlo.ships.scan;

/** Bounded result of a ship assembly scan. */
public final class ScanResult {
  private final boolean complete;
  private final int rootX;
  private final int rootY;
  private final int rootZ;
  private final java.util.List<dev.jlo.ships.model.BlockPos> captured;

  /** Creates a successful scan result. */
  public ScanResult(int rootX, int rootY, int rootZ, java.util.List<dev.jlo.ships.model.BlockPos> captured) {
    this.complete = true;
    this.rootX = rootX;
    this.rootY = rootY;
    this.rootZ = rootZ;
    this.captured = captured;
  }

  /** Creates a failed scan result with no captured blocks. */
  public ScanResult() {
    this.complete = false;
    this.rootX = 0;
    this.rootY = 0;
    this.rootZ = 0;
    this.captured = null;
  }

  /** Returns true when the whole component fit within the limit. */
  public boolean complete() {
    return complete;
  }

  /** Returns the scan seed x coordinate. */
  public int rootX() {
    return rootX;
  }

  /** Returns the scan seed y coordinate. */
  public int rootY() {
    return rootY;
  }

  /** Returns the scan seed z coordinate. */
  public int rootZ() {
    return rootZ;
  }

  /** Returns captured relative positions, or null when incomplete. */
  public java.util.List<dev.jlo.ships.model.BlockPos> captured() {
    return captured;
  }
}