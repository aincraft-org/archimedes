package dev.mintychochip.archimedes.phys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Formats a {@link ShipInspection} as player-facing lines. */
public final class ShipInspectionLines {
  private ShipInspectionLines() {}

  /**
   * Builds inspect lines without color codes.
   *
   * @param report snapshot to format
   * @return ordered report lines
   */
  public static List<String> lines(ShipInspection report) {
    List<String> lines = new ArrayList<>();
    String id = report.shipId().toString().substring(0, 8);
    lines.add(
        String.format(
            Locale.ROOT,
            "Arch %s | blocks=%d cloth=%d riders=%d mass=%.1f",
            id,
            report.blocks(),
            report.cloth(),
            report.riders(),
            report.mass()));
    lines.add(
        String.format(
            Locale.ROOT,
            "pose=%.3f,%.3f,%.3f vel=%.3f,%.3f,%.3f",
            report.poseX(),
            report.poseY(),
            report.poseZ(),
            report.velX(),
            report.velY(),
            report.velZ()));
    lines.add(
        String.format(
            Locale.ROOT,
            "buoyancy=%s chunks=%s submerged=%d",
            report.buoyancyEnabled() ? "on" : "off",
            report.chunksLoaded() ? "loaded" : "unloaded",
            report.submerged()));
    lines.add(
        String.format(
            Locale.ROOT,
            "tick=%.3fms sample=%.3fms",
            report.lastTickNanos() / 1_000_000.0,
            report.sampleNanos() / 1_000_000.0));
    for (ShipInspection.ForceLine force : report.forces()) {
      lines.add(
          String.format(
              Locale.ROOT,
              "F %s %.2f,%.2f,%.2f  t=%.2f,%.2f,%.2f",
              force.name(),
              force.fx(),
              force.fy(),
              force.fz(),
              force.tx(),
              force.ty(),
              force.tz()));
    }
    lines.add(
        String.format(
            Locale.ROOT, "net %.2f,%.2f,%.2f", report.netFx(), report.netFy(), report.netFz()));
    return lines;
  }
}
