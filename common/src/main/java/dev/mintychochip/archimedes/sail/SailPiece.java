package dev.mintychochip.archimedes.sail;

import java.util.Objects;

/**
 * One transformed BlockDisplay plate in ship-local coordinates.
 *
 * <p>The display entity is spawned at {@code (originX, originY, originZ)}. Scale and rotation are
 * applied as a Paper {@code Transformation}; identity rotation with a non-unit scale is a thin
 * axis-aligned plate.
 *
 * @param originX local min-corner x of the plate
 * @param originY local min-corner y of the plate
 * @param originZ local min-corner z of the plate
 * @param scaleX display scale x
 * @param scaleY display scale y
 * @param scaleZ display scale z
 * @param rotX quaternion x
 * @param rotY quaternion y
 * @param rotZ quaternion z
 * @param rotW quaternion w
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
    String appearance) {
  /**
   * @param originX local min-corner x
   * @param originY local min-corner y
   * @param originZ local min-corner z
   * @param scaleX display scale x
   * @param scaleY display scale y
   * @param scaleZ display scale z
   * @param rotX quaternion x
   * @param rotY quaternion y
   * @param rotZ quaternion z
   * @param rotW quaternion w
   * @param appearance serialized block data
   */
  public SailPiece {
    Objects.requireNonNull(appearance, "appearance");
  }
}
