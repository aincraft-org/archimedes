package dev.mintychochip.archimedes.sail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavior tests for the Paper-free cloth region → BlockDisplay plate transform. */
class SailMeshTest {
  private static final String WHITE_WOOL = "minecraft:white_wool";
  private static final String RED_WOOL = "minecraft:red_wool";
  private static final double EPS = 1e-9;

  @Test
  void isolatedClothCellEmitsOneThinPlateWithCapturedAppearance() {
    List<SailCell> cells = List.of(new SailCell(5, 6, 7, WHITE_WOOL));

    List<SailPiece> pieces = SailMesh.tessellate(cells);

    assertEquals(1, pieces.size());
    SailPiece piece = pieces.get(0);
    assertTrue(isTransformedPlate(piece), "single cell must still be a thin plate, not a unit cube");
    assertEquals(WHITE_WOOL, piece.appearance());
    assertSheetCoversRegion(cells, pieces);
  }

  @Test
  void flatMultiCellWallEmitsASeriesOfPlatesCoveringTheWall() {
    List<SailCell> cells = new ArrayList<>();
    for (int x = 0; x < 3; x++) {
      for (int y = 0; y < 2; y++) {
        cells.add(new SailCell(x, y, 4, WHITE_WOOL));
      }
    }

    List<SailPiece> pieces = SailMesh.tessellate(cells);

    assertTrue(pieces.size() > 1, "multi-cell wall must tessellate into a series of plates");
    assertEquals(6, pieces.size());
    for (SailPiece piece : pieces) {
      assertTrue(isTransformedPlate(piece));
      assertEquals(WHITE_WOOL, piece.appearance());
    }
    assertSheetCoversRegion(cells, pieces);
    assertEveryCellIntersectedOrSheetMatches(cells, pieces);
  }

  @Test
  void thickVolumeFitsASheetOfMultipleThinPlates() {
    List<SailCell> cells = new ArrayList<>();
    for (int x = 0; x < 2; x++) {
      for (int y = 0; y < 2; y++) {
        for (int z = 0; z < 2; z++) {
          cells.add(new SailCell(x, y, z, RED_WOOL));
        }
      }
    }

    List<SailPiece> pieces = SailMesh.tessellate(cells);

    assertTrue(pieces.size() > 1, "thick volume must still emit more than one plate");
    for (SailPiece piece : pieces) {
      assertTrue(isTransformedPlate(piece));
      assertEquals(RED_WOOL, piece.appearance());
    }
    assertSheetCoversRegion(cells, pieces);
    assertUnionIsASheet(pieces);
  }

  @Test
  void emptyRegionEmitsNoPieces() {
    assertTrue(SailMesh.tessellate(List.of()).isEmpty());
  }

  @Test
  void clothDetectionMatchesWoolAndBannersOnly() {
    assertTrue(SailMesh.isCloth(WHITE_WOOL));
    assertTrue(SailMesh.isCloth("minecraft:red_banner[rotation=0]"));
    assertTrue(SailMesh.isCloth("minecraft:white_wall_banner[facing=south]"));
    assertFalse(SailMesh.isCloth("minecraft:stone"));
    assertFalse(SailMesh.isCloth("minecraft:oak_planks"));
  }

  private static boolean isTransformedPlate(SailPiece piece) {
    boolean thinScale =
        !isUnit(piece.scaleX()) || !isUnit(piece.scaleY()) || !isUnit(piece.scaleZ());
    boolean rotated = !isIdentityRotation(piece);
    return thinScale || rotated;
  }

  private static boolean isUnit(double scale) {
    return Math.abs(scale - 1.0) < EPS;
  }

  private static boolean isIdentityRotation(SailPiece piece) {
    return Math.abs(piece.rotX()) < EPS
        && Math.abs(piece.rotY()) < EPS
        && Math.abs(piece.rotZ()) < EPS
        && Math.abs(piece.rotW() - 1.0) < EPS;
  }

  private static void assertSheetCoversRegion(List<SailCell> cells, List<SailPiece> pieces) {
    Bounds region = regionBounds(cells);
    Bounds sheet = sheetBounds(pieces);
    boolean xThin = approx(sheet.spanX(), SailMesh.PLATE_THICKNESS);
    boolean yThin = approx(sheet.spanY(), SailMesh.PLATE_THICKNESS);
    boolean zThin = approx(sheet.spanZ(), SailMesh.PLATE_THICKNESS);
    boolean xCover = approx(sheet.minX, region.minX) && approx(sheet.maxX, region.maxX);
    boolean yCover = approx(sheet.minY, region.minY) && approx(sheet.maxY, region.maxY);
    boolean zCover = approx(sheet.minZ, region.minZ) && approx(sheet.maxZ, region.maxZ);
    assertTrue(xThin || yThin || zThin, "sheet must be thin on at least one axis");
    assertTrue(xThin || xCover, "sheet must span the region X extent");
    assertTrue(yThin || yCover, "sheet must span the region Y extent");
    assertTrue(zThin || zCover, "sheet must span the region Z extent");
  }

  private static void assertEveryCellIntersectedOrSheetMatches(
      List<SailCell> cells, List<SailPiece> pieces) {
    boolean allHit = true;
    for (SailCell cell : cells) {
      if (!intersectsCell(pieces, cell)) {
        allHit = false;
        break;
      }
    }
    if (allHit) {
      return;
    }
    assertSheetCoversRegion(cells, pieces);
  }

  private static void assertUnionIsASheet(List<SailPiece> pieces) {
    Bounds sheet = sheetBounds(pieces);
    int thinAxes = 0;
    if (approx(sheet.spanX(), SailMesh.PLATE_THICKNESS)) {
      thinAxes++;
    }
    if (approx(sheet.spanY(), SailMesh.PLATE_THICKNESS)) {
      thinAxes++;
    }
    if (approx(sheet.spanZ(), SailMesh.PLATE_THICKNESS)) {
      thinAxes++;
    }
    assertEquals(1, thinAxes, "fitted sheet union must be thin on exactly one axis");
  }

  private static boolean intersectsCell(List<SailPiece> pieces, SailCell cell) {
    for (SailPiece piece : pieces) {
      Bounds box = pieceBox(piece);
      if (box.maxX > cell.x()
          && box.minX < cell.x() + 1
          && box.maxY > cell.y()
          && box.minY < cell.y() + 1
          && box.maxZ > cell.z()
          && box.minZ < cell.z() + 1) {
        return true;
      }
    }
    return false;
  }

  private static Bounds regionBounds(List<SailCell> cells) {
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (SailCell cell : cells) {
      minX = Math.min(minX, cell.x());
      minY = Math.min(minY, cell.y());
      minZ = Math.min(minZ, cell.z());
      maxX = Math.max(maxX, cell.x());
      maxY = Math.max(maxY, cell.y());
      maxZ = Math.max(maxZ, cell.z());
    }
    return new Bounds(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
  }

  private static Bounds sheetBounds(List<SailPiece> pieces) {
    assertFalse(pieces.isEmpty());
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double minZ = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    double maxZ = Double.NEGATIVE_INFINITY;
    for (SailPiece piece : pieces) {
      Bounds box = pieceBox(piece);
      minX = Math.min(minX, box.minX);
      minY = Math.min(minY, box.minY);
      minZ = Math.min(minZ, box.minZ);
      maxX = Math.max(maxX, box.maxX);
      maxY = Math.max(maxY, box.maxY);
      maxZ = Math.max(maxZ, box.maxZ);
    }
    return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
  }

  private static Bounds pieceBox(SailPiece piece) {
    return new Bounds(
        piece.originX(),
        piece.originY(),
        piece.originZ(),
        piece.originX() + piece.scaleX(),
        piece.originY() + piece.scaleY(),
        piece.originZ() + piece.scaleZ());
  }

  private static boolean approx(double a, double b) {
    return Math.abs(a - b) < 1e-6;
  }

  private static final class Bounds {
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    private Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      this.minX = minX;
      this.minY = minY;
      this.minZ = minZ;
      this.maxX = maxX;
      this.maxY = maxY;
      this.maxZ = maxZ;
    }

    private double spanX() {
      return maxX - minX;
    }

    private double spanY() {
      return maxY - minY;
    }

    private double spanZ() {
      return maxZ - minZ;
    }
  }
}
