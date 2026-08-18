package dev.mintychochip.archimedes.phys;

import java.util.List;
import java.util.UUID;

/**
 * Point-in-time ship diagnostics: pose, mass factors, last-tick cost, and sampled forces.
 *
 * @param shipId ship identifier
 * @param blocks captured block count
 * @param cloth cloth cell count
 * @param riders tracked rider count
 * @param mass assembled mass including riders
 * @param buoyancyEnabled whether the ship is simulating
 * @param chunksLoaded whether every occupied chunk is in the loaded cache
 * @param poseX pose east-west
 * @param poseY pose vertical
 * @param poseZ pose north-south
 * @param velX retained linear x
 * @param velY retained linear y
 * @param velZ retained linear z
 * @param submerged submerged collider count
 * @param lastTickNanos last {@code tick} duration, or 0 if none
 * @param sampleNanos time spent sampling forces for this report
 * @param forces per-law force and torque samples
 * @param netFx net force x
 * @param netFy net force y
 * @param netFz net force z
 */
public record ShipInspection(
    UUID shipId,
    int blocks,
    int cloth,
    int riders,
    double mass,
    boolean buoyancyEnabled,
    boolean chunksLoaded,
    double poseX,
    double poseY,
    double poseZ,
    double velX,
    double velY,
    double velZ,
    int submerged,
    long lastTickNanos,
    long sampleNanos,
    List<ForceLine> forces,
    double netFx,
    double netFy,
    double netFz) {
  /**
   * One attached force law's contribution.
   *
   * @param name short law name
   * @param fx force x
   * @param fy force y
   * @param fz force z
   * @param tx torque x
   * @param ty torque y
   * @param tz torque z
   */
  public record ForceLine(
      String name, double fx, double fy, double fz, double tx, double ty, double tz) {}
}
