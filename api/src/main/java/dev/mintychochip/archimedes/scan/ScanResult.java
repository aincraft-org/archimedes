package dev.mintychochip.archimedes.scan;

import dev.mintychochip.archimedes.model.BlockPos;
import java.util.List;

/** Bounded scan result; captured positions are copied when present and absent on failure. */
public final class ScanResult {
  /** Whether the whole component fit within the limit. */
  private final boolean complete;

  /** Scan seed x coordinate. */
  private final int rootX;

  /** Scan seed y coordinate. */
  private final int rootY;

  /** Scan seed z coordinate. */
  private final int rootZ;

  /** Captured relative positions in supplied iteration order, or null when incomplete. */
  private final List<BlockPos> captured;

  /**
   * Creates a successful scan result.
   *
   * <p>The captured list is copied in iteration order and rejects null elements. A null {@code
   * captured} argument is retained as {@code null}, despite this result being marked complete;
   * other arguments are primitive values and therefore always present.
   *
   * @param rootX the scan seed x coordinate
   * @param rootY the scan seed y coordinate
   * @param rootZ the scan seed z coordinate
   * @param captured the captured relative positions; may be {@code null}
   */
  public ScanResult(int rootX, int rootY, int rootZ, List<BlockPos> captured) {
    this.complete = true;
    this.rootX = rootX;
    this.rootY = rootY;
    this.rootZ = rootZ;
    this.captured = captured == null ? null : List.copyOf(captured);
  }

  /** Creates an incomplete scan result with null captured positions and zero root coordinates. */
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
   * @return captured relative positions in supplied iteration order as an unmodifiable list, or
   *     {@code null} when incomplete (or when a null list was supplied to the successful-result
   *     constructor)
   */
  public List<BlockPos> captured() {
    return captured;
  }
}
