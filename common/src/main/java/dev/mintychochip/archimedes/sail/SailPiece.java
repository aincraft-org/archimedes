package dev.mintychochip.archimedes.sail;

import java.util.Objects;

/**
 * One transformed BlockDisplay plate in ship-local coordinates.
 *
 * <p>The display entity is spawned at {@code (originX, originY, originZ)}. Paper applies {@code
 * origin + R_left * scale * R_right * local} for local cube points in {@code [0,1]}. Identity
 * rotations with a non-unit scale is a thin axis-aligned plate.
 *
 * @param originX local min-corner x of the plate
 * @param originY local min-corner y of the plate
 * @param originZ local min-corner z of the plate
 * @param scaleX display scale x
 * @param scaleY display scale y
 * @param scaleZ display scale z
 * @param rotX left quaternion x
 * @param rotY left quaternion y
 * @param rotZ left quaternion z
 * @param rotW left quaternion w
 * @param rightX right quaternion x
 * @param rightY right quaternion y
 * @param rightZ right quaternion z
 * @param rightW right quaternion w
 * @param appearance serialized block data for {@code setBlock}
 */
public record SailPiece(
    double originX,
    double originY,
    double originZ,
    double scaleX,
    double scaleY,
    double scaleZ,
    double rotX,
    double rotY,
    double rotZ,
    double rotW,
    double rightX,
    double rightY,
    double rightZ,
    double rightW,
    String appearance) {
  /**
   * @param originX local min-corner x
   * @param originY local min-corner y
   * @param originZ local min-corner z
   * @param scaleX display scale x
   * @param scaleY display scale y
   * @param scaleZ display scale z
   * @param rotX left quaternion x
   * @param rotY left quaternion y
   * @param rotZ left quaternion z
   * @param rotW left quaternion w
   * @param rightX right quaternion x
   * @param rightY right quaternion y
   * @param rightZ right quaternion z
   * @param rightW right quaternion w
   * @param appearance serialized block data
   */
  public SailPiece {
    Objects.requireNonNull(appearance, "appearance");
  }

  /**
   * Axis-aligned or left-rotated plate with identity right rotation.
   *
   * @param originX local min-corner x
   * @param originY local min-corner y
   * @param originZ local min-corner z
   * @param scaleX display scale x
   * @param scaleY display scale y
   * @param scaleZ display scale z
   * @param rotX left quaternion x
   * @param rotY left quaternion y
   * @param rotZ left quaternion z
   * @param rotW left quaternion w
   * @param appearance serialized block data
   */
  public SailPiece(
      double originX,
      double originY,
      double originZ,
      double scaleX,
      double scaleY,
      double scaleZ,
      double rotX,
      double rotY,
      double rotZ,
      double rotW,
      String appearance) {
    this(
        originX,
        originY,
        originZ,
        scaleX,
        scaleY,
        scaleZ,
        rotX,
        rotY,
        rotZ,
        rotW,
        0.0,
        0.0,
        0.0,
        1.0,
        appearance);
  }
}
