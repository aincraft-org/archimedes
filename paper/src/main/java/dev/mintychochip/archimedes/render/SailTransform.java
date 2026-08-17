package dev.mintychochip.archimedes.render;

import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.sail.SailPiece;
import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Maps Paper-free sail pieces onto BlockDisplay locations and transformations. */
public final class SailTransform {
  private SailTransform() {}

  /**
   * Projects a piece's local origin through the ship's current pose.
   *
   * @param surface world surface
   * @param ship ship supplying origin and pose
   * @param piece plate in ship-local coordinates
   * @return world location of the plate's min corner
   */
  public static Location location(RenderSurface surface, Ship ship, SailPiece piece) {
    return surface.location(
        ship.origin(),
        ship.pose().x() + piece.originX(),
        ship.pose().y() + piece.originY(),
        ship.pose().z() + piece.originZ());
  }

  /**
   * Builds the affine plate transform (identity translation, piece scale and rotation).
   *
   * @param piece plate geometry
   * @return Paper display transformation
   */
  public static Transformation transformation(SailPiece piece) {
    return new Transformation(
        new Vector3f(),
        new Quaternionf(
            (float) piece.rotX(), (float) piece.rotY(), (float) piece.rotZ(), (float) piece.rotW()),
        new Vector3f((float) piece.scaleX(), (float) piece.scaleY(), (float) piece.scaleZ()),
        new Quaternionf());
  }
}
