package dev.mintychochip.archimedes.phys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Formats a {@link ShipInspection} as player-facing lines. */
public final class ShipInspectionLines {
  /** Reset formatting. */
  private static final String RESET = "\u00A7r";

  /** X-component color. */
  private static final String RED = "\u00A7c";

  /** Y-component color. */
  private static final String GREEN = "\u00A7a";

  /** Z-component color. */
  private static final String AQUA = "\u00A7b";

  /** Header color. */
  private static final String GOLD = "\u00A76";

  /** Sail-name color. */
  private static final String YELLOW = "\u00A7e";

  /** Gravity-name color. */
  private static final String GRAY = "\u00A77";

  /** Net-line color. */
  private static final String WHITE = "\u00A7f";

  /** Buoyancy-name color. */
  private static final String BLUE = "\u00A79";

  /** Water-drag-name color. */
  private static final String DARK_AQUA = "\u00A73";

  /** Air-drag-name color. */
  private static final String DARK_GRAY = "\u00A78";

  /** Vegetation-name color. */
  private static final String DARK_GREEN = "\u00A72";

  private ShipInspectionLines() {}

  /**
   * Builds inspect lines with Minecraft color codes on force-vector components.
   *
   * <p>X is red, Y is green, Z is aqua. Force names have a stable per-law color.
   *
   * @param report snapshot to format
   * @return ordered report lines
   */
  public static List<String> lines(ShipInspection report) {
    List<String> lines = new ArrayList<>();
    String id = report.shipId().toString().substring(0, 8);
    lines.add(
        GOLD
            + String.format(
                Locale.ROOT,
                "Arch %s | blocks=%d cloth=%d riders=%d mass=%.1f",
                id,
                report.blocks(),
                report.cloth(),
                report.riders(),
                report.mass()));
    lines.add(
        GOLD
            + String.format(
                Locale.ROOT,
                "pose=%.3f,%.3f,%.3f vel=%.3f,%.3f,%.3f",
                report.poseX(),
                report.poseY(),
                report.poseZ(),
                report.velX(),
                report.velY(),
                report.velZ()));
    lines.add(
        GOLD
            + String.format(
                Locale.ROOT,
                "buoyancy=%s chunks=%s submerged=%d",
                report.buoyancyEnabled() ? "on" : "off",
                report.chunksLoaded() ? "loaded" : "unloaded",
                report.submerged()));
    lines.add(
        GOLD
            + String.format(
                Locale.ROOT,
                "tick=%.3fms sample=%.3fms",
                report.lastTickNanos() / 1_000_000.0,
                report.sampleNanos() / 1_000_000.0));
    for (ShipInspection.ForceLine force : report.forces()) {
      lines.add(
          nameColor(force.name())
              + "F "
              + force.name()
              + RESET
              + " "
              + vector(force.fx(), force.fy(), force.fz())
              + "  t="
              + vector(force.tx(), force.ty(), force.tz()));
    }
    lines.add(WHITE + "net " + RESET + vector(report.netFx(), report.netFy(), report.netFz()));
    return lines;
  }

  /**
   * @param name inspect law label
   * @return Minecraft color prefix for that law
   */
  private static String nameColor(String name) {
    if (name.startsWith("Sail")) {
      return YELLOW;
    }
    if ("Gravity".equals(name)) {
      return GRAY;
    }
    if ("Buoyancy".equals(name)) {
      return BLUE;
    }
    if ("WaterDrag".equals(name)) {
      return DARK_AQUA;
    }
    if ("Drag".equals(name)) {
      return DARK_GRAY;
    }
    if ("Vegetation".equals(name)) {
      return DARK_GREEN;
    }
    return WHITE;
  }

  /**
   * Formats a vector with X red, Y green, and Z aqua.
   *
   * @param x x component
   * @param y y component
   * @param z z component
   * @return colored {@code x,y,z} text
   */
  private static String vector(double x, double y, double z) {
    return RED + format(x) + RESET + "," + GREEN + format(y) + RESET + "," + AQUA + format(z)
        + RESET;
  }

  /**
   * @param value component magnitude
   * @return two-decimal component text
   */
  private static String format(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
