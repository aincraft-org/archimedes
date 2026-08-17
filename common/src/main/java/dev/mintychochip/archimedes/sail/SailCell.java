package dev.mintychochip.archimedes.sail;

import java.util.Objects;

/**
 * One integer cell of a cloth region plus the captured block appearance.
 *
 * @param x relative cell x
 * @param y relative cell y
 * @param z relative cell z
 * @param appearance serialized block data used as the plate's {@code BlockData}
 */
public record SailCell(int x, int y, int z, String appearance) {
  /**
   * @param x relative cell x
   * @param y relative cell y
   * @param z relative cell z
   * @param appearance serialized block data
   */
  public SailCell {
    Objects.requireNonNull(appearance, "appearance");
  }
}
