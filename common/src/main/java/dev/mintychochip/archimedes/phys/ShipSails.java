package dev.mintychochip.archimedes.phys;

import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.phys.DensityField;
import dev.mintychochip.phys.FlowField;
import dev.mintychochip.phys.Force;
import dev.mintychochip.phys.PressureSailForce;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Turns marked blocks on a ship into {@link PressureSailForce} units.
 *
 * <p>Each sail block is one square metre of cloth at its block center. {@code facing=} in the
 * captured block data is the cloth normal; missing facing defaults to {@code +Z} (south).
 */
public final class ShipSails {
  private ShipSails() {}

  /**
   * Builds one pressure sail per block whose resolved material is in {@code sailKeys}.
   *
   * @param ship captured structure
   * @param resolver material-key resolver
   * @param sailKeys materials treated as cloth
   * @param air air density
   * @param wind flow field
   * @return immutable list of sail forces, in block iteration order
   */
  public static List<Force> forces(
      Vehicle ship,
      MaterialKeyResolver resolver,
      Set<String> sailKeys,
      DensityField air,
      FlowField wind) {
    Objects.requireNonNull(ship);
    Objects.requireNonNull(resolver);
    Objects.requireNonNull(sailKeys);
    Objects.requireNonNull(air);
    Objects.requireNonNull(wind);
    List<Force> sails = new ArrayList<>();
    for (ShipBlock block : ship.blocks()) {
      if (!sailKeys.contains(resolver.key(block))) {
        continue;
      }
      Vector3d point =
          new Vector3d(block.pos().x() + 0.5, block.pos().y() + 0.5, block.pos().z() + 0.5);
      sails.add(new PressureSailForce(point, facingNormal(block.blockData()), 1.0, air, wind));
    }
    return List.copyOf(sails);
  }

  static Vector3dc facingNormal(String blockData) {
    String facing = facingValue(blockData);
    if ("north".equals(facing)) {
      return new Vector3d(0, 0, -1);
    }
    if ("south".equals(facing)) {
      return new Vector3d(0, 0, 1);
    }
    if ("west".equals(facing)) {
      return new Vector3d(-1, 0, 0);
    }
    if ("east".equals(facing)) {
      return new Vector3d(1, 0, 0);
    }
    if ("up".equals(facing)) {
      return new Vector3d(0, 1, 0);
    }
    if ("down".equals(facing)) {
      return new Vector3d(0, -1, 0);
    }
    return new Vector3d(0, 0, 1);
  }

  private static String facingValue(String blockData) {
    int start = blockData.indexOf("facing=");
    if (start < 0) {
      return "";
    }
    start += "facing=".length();
    int end = start;
    while (end < blockData.length()) {
      char ch = blockData.charAt(end);
      if (ch == ',' || ch == ']') {
        break;
      }
      end++;
    }
    return blockData.substring(start, end).toLowerCase(Locale.ROOT);
  }
}
