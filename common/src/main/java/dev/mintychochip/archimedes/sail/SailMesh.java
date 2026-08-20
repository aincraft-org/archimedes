package dev.mintychochip.archimedes.sail;

import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.phys.FlowField;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Turns a 3D region of cloth cells into a series of thin BlockDisplay plates.
 *
 * <p>Connected cells form one region. Still air keeps a 1-cell-thick wall as a cardinal sheet and a
 * multi-depth region as stacked plates. Wind cups the in-plane grid into a connected cloth: vertex
 * belly varies across the sheet and each plate is rotated to the local surface, not one cardinal
 * tilt. Geometry is computed from the region; no item model or resource pack is involved.
 */
public final class SailMesh {
  /**
   * Plate thickness in blocks: one Minecraft pixel. The fitted sheet matches the region AABB on the
   * two in-plane axes and is this thick on the remaining axis.
   */
  public static final double PLATE_THICKNESS = 1.0 / 16.0;

  /** Wind speed (m/s) that produces a full belly offset. Matches the plugin default breeze. */
  private static final double BILLOW_REF_SPEED = 8.0;

  /** Maximum center belly along the wind, in blocks, at {@link #BILLOW_REF_SPEED}. */
  private static final double BILLOW_DEPTH = 1.25;

  /** Face-adjacent offsets used to group connected cloth into regions. */
  private static final int[][] NEIGHBORS = {
    {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
  };

  /** Deterministic cell order: x, then y, then z, then appearance. */
  private static final Comparator<SailCell> CELL_ORDER =
      Comparator.comparingInt(SailCell::x)
          .thenComparingInt(SailCell::y)
          .thenComparingInt(SailCell::z)
          .thenComparing(SailCell::appearance);

  private SailMesh() {}

  /**
   * Whether serialized block data is cloth ({@code *_wool}, {@code *_banner}, {@code
   * *_wall_banner}).
   *
   * @param blockData captured block data or material key
   * @return whether the block is drawn as a tessellated sail
   */
  public static boolean isCloth(String blockData) {
    String key = materialKey(Objects.requireNonNull(blockData, "blockData"));
    return key.endsWith("_wool") || key.endsWith("_banner") || key.endsWith("_wall_banner");
  }

  /**
   * Collects cloth cells from captured ship blocks, preserving iteration order.
   *
   * @param blocks captured ship blocks
   * @return cloth cells ready for {@link #tessellate(Collection)}
   */
  public static List<SailCell> cellsOf(Iterable<ShipBlock> blocks) {
    Objects.requireNonNull(blocks, "blocks");
    List<SailCell> cells = new ArrayList<>();
    for (ShipBlock block : blocks) {
      if (isCloth(block.blockData())) {
        cells.add(
            new SailCell(block.pos().x(), block.pos().y(), block.pos().z(), block.blockData()));
      }
    }
    return cells;
  }

  /**
   * Tessellates cloth cells into thin plates covering each connected region in still air.
   *
   * @param cells integer cells plus captured appearances
   * @return immutable piece list in deterministic region / cell order
   */
  public static List<SailPiece> tessellate(Collection<SailCell> cells) {
    return tessellate(cells, FlowField.still());
  }

  /**
   * Tessellates cloth cells and billows plates from the wind sampled at the cloth centroid.
   *
   * @param cells integer cells plus captured appearances
   * @param wind flow field sampled for apparent-wind billow
   * @return immutable piece list in deterministic region / cell order
   */
  public static List<SailPiece> tessellate(Collection<SailCell> cells, FlowField wind) {
    Objects.requireNonNull(cells, "cells");
    Objects.requireNonNull(wind, "wind");
    if (cells.isEmpty()) {
      return List.of();
    }
    return tessellate(cells, wind.velocity(centroid(cells)));
  }

  /**
   * Maps a local cube point {@code (lx,ly,lz)} in {@code [0,1]} through the plate's Paper
   * transform: {@code origin + R_left * scale * R_right * local}.
   *
   * @param piece plate
   * @param lx local x
   * @param ly local y
   * @param lz local z
   * @return ship-local world point
   */
  public static Vector3d localToWorld(SailPiece piece, double lx, double ly, double lz) {
    Objects.requireNonNull(piece, "piece");
    Quaterniond right =
        new Quaterniond(piece.rightX(), piece.rightY(), piece.rightZ(), piece.rightW());
    Quaterniond left = new Quaterniond(piece.rotX(), piece.rotY(), piece.rotZ(), piece.rotW());
    Vector3d local = right.transform(new Vector3d(lx, ly, lz));
    local.set(local.x * piece.scaleX(), local.y * piece.scaleY(), local.z * piece.scaleZ());
    left.transform(local);
    return local.add(piece.originX(), piece.originY(), piece.originZ());
  }

  /**
   * Counts cloth-face edges (local z=0 rectangle) that are not shared with another plate.
   *
   * <p>A watertight w×h sheet has only the outer boundary unmatched: {@code 2*(w+h)} edges.
   *
   * @param pieces tessellated plates
   * @return number of edges that appear on exactly one plate
   */
  public static int unsharedClothEdges(List<SailPiece> pieces) {
    Objects.requireNonNull(pieces, "pieces");
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (SailPiece piece : pieces) {
      Vector3d v00 = localToWorld(piece, 0, 0, 0);
      Vector3d v10 = localToWorld(piece, 1, 0, 0);
      Vector3d v01 = localToWorld(piece, 0, 1, 0);
      Vector3d v11 = localToWorld(piece, 1, 1, 0);
      tally(counts, v00, v10);
      tally(counts, v10, v11);
      tally(counts, v11, v01);
      tally(counts, v01, v00);
    }
    int unshared = 0;
    for (int count : counts.values()) {
      if (count == 1) {
        unshared++;
      }
    }
    return unshared;
  }

  private static void tally(Map<String, Integer> counts, Vector3d a, Vector3d b) {
    String ka = pointKey(a);
    String kb = pointKey(b);
    String key = ka.compareTo(kb) < 0 ? ka + "|" + kb : kb + "|" + ka;
    counts.merge(key, 1, Integer::sum);
  }

  private static String pointKey(Vector3d point) {
    return Math.round(point.x * 1_000_000.0)
        + ","
        + Math.round(point.y * 1_000_000.0)
        + ","
        + Math.round(point.z * 1_000_000.0);
  }

  /**
   * Tessellates cloth cells and billows plates along the given apparent-wind vector.
   *
   * @param cells integer cells plus captured appearances
   * @param apparentWind wind relative to the cloth ({@code v_wind − v_ship})
   * @return immutable piece list in deterministic region / cell order
   */
  public static List<SailPiece> tessellate(Collection<SailCell> cells, Vector3dc apparentWind) {
    Objects.requireNonNull(cells, "cells");
    Objects.requireNonNull(apparentWind, "apparentWind");
    if (cells.isEmpty()) {
      return List.of();
    }
    Map<CellKey, SailCell> byKey = index(cells);
    Set<CellKey> remaining = new HashSet<>(byKey.keySet());
    List<SailPiece> pieces = new ArrayList<>();
    for (SailCell seed : byKey.values()) {
      CellKey seedKey = new CellKey(seed.x(), seed.y(), seed.z());
      if (!remaining.contains(seedKey)) {
        continue;
      }
      pieces.addAll(cloth(flood(seed, byKey, remaining), apparentWind));
    }
    return List.copyOf(pieces);
  }

  private static Map<CellKey, SailCell> index(Collection<SailCell> cells) {
    List<SailCell> sorted = new ArrayList<>(cells.size());
    for (SailCell cell : cells) {
      sorted.add(Objects.requireNonNull(cell, "cell"));
    }
    sorted.sort(CELL_ORDER);
    Map<CellKey, SailCell> byKey = new LinkedHashMap<>();
    for (SailCell cell : sorted) {
      byKey.putIfAbsent(new CellKey(cell.x(), cell.y(), cell.z()), cell);
    }
    return byKey;
  }

  private static List<SailCell> flood(
      SailCell seed, Map<CellKey, SailCell> byKey, Set<CellKey> remaining) {
    List<SailCell> region = new ArrayList<>();
    ArrayDeque<SailCell> queue = new ArrayDeque<>();
    queue.add(seed);
    remaining.remove(new CellKey(seed.x(), seed.y(), seed.z()));
    while (!queue.isEmpty()) {
      SailCell current = queue.removeFirst();
      region.add(current);
      for (int[] delta : NEIGHBORS) {
        CellKey next =
            new CellKey(current.x() + delta[0], current.y() + delta[1], current.z() + delta[2]);
        if (remaining.remove(next)) {
          queue.add(byKey.get(next));
        }
      }
    }
    region.sort(CELL_ORDER);
    return region;
  }

  private static List<SailPiece> cloth(List<SailCell> region, Vector3dc apparentWind) {
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (SailCell cell : region) {
      minX = Math.min(minX, cell.x());
      minY = Math.min(minY, cell.y());
      minZ = Math.min(minZ, cell.z());
      maxX = Math.max(maxX, cell.x());
      maxY = Math.max(maxY, cell.y());
      maxZ = Math.max(maxZ, cell.z());
    }
    int axis =
        thinAxis(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, region.get(0).appearance());
    if (apparentWind.length() < 1e-9) {
      List<SailPiece> pieces = new ArrayList<>(region.size());
      for (SailCell cell : region) {
        pieces.add(plate(cell, axis, thinOrigin(cell, axis)));
      }
      return pieces;
    }
    return cup(region, axis, apparentWind);
  }

  private static double thinOrigin(SailCell cell, int axis) {
    int coord = axis == 0 ? cell.x() : axis == 1 ? cell.y() : cell.z();
    return coord + 0.5 - PLATE_THICKNESS * 0.5;
  }

  private static List<SailPiece> cup(List<SailCell> region, int axis, Vector3dc apparentWind) {
    Map<PlaneKey, SailCell> plane = new LinkedHashMap<>();
    Map<PlaneKey, Double> restThin = new LinkedHashMap<>();
    int minU = Integer.MAX_VALUE;
    int minV = Integer.MAX_VALUE;
    int maxU = Integer.MIN_VALUE;
    int maxV = Integer.MIN_VALUE;
    for (SailCell cell : region) {
      int u = planeU(axis, cell);
      int v = planeV(axis, cell);
      PlaneKey key = new PlaneKey(u, v);
      plane.putIfAbsent(key, cell);
      restThin.merge(key, thinOrigin(cell, axis) + PLATE_THICKNESS * 0.5, Math::min);
      minU = Math.min(minU, u);
      minV = Math.min(minV, v);
      maxU = Math.max(maxU, u);
      maxV = Math.max(maxV, v);
    }
    int spanU = maxU - minU + 1;
    int spanV = maxV - minV + 1;
    double speed = apparentWind.length();
    double strength = Math.min(speed / BILLOW_REF_SPEED, 1.0) * BILLOW_DEPTH;
    Vector3d windDir = new Vector3d(apparentWind).div(speed);
    double fallbackThin = 0;
    for (double thin : restThin.values()) {
      fallbackThin += thin;
    }
    fallbackThin /= restThin.size();
    double[][] target = new double[spanU + 1][spanV + 1];
    for (int i = 0; i <= spanU; i++) {
      for (int j = 0; j <= spanV; j++) {
        double uNorm = spanU == 0 ? 0.5 : i / (double) spanU;
        double vNorm = spanV == 0 ? 0.5 : j / (double) spanV;
        double fromMast = Math.abs(uNorm - 0.5) * 2.0;
        double hoist = Math.sin(Math.PI * vNorm);
        target[i][j] = (0.15 + 0.85 * fromMast * fromMast) * (0.15 + 0.85 * hoist);
      }
    }
    double[][] cup = projectParallelograms(target);
    Vector3d[][] verts = new Vector3d[spanU + 1][spanV + 1];
    for (int i = 0; i <= spanU; i++) {
      for (int j = 0; j <= spanV; j++) {
        int u = minU + i;
        int v = minV + j;
        double thin = vertexThin(u, v, restThin, fallbackThin);
        Vector3d rest = unproject(axis, u, v, thin);
        double belly = strength * cup[i][j];
        verts[i][j] =
            new Vector3d(
                rest.x + windDir.x * belly,
                rest.y + windDir.y * belly,
                rest.z + windDir.z * belly);
      }
    }
    List<SailPiece> pieces = new ArrayList<>(plane.size());
    for (SailCell cell : plane.values()) {
      int i = planeU(axis, cell) - minU;
      int j = planeV(axis, cell) - minV;
      pieces.add(
          affinePlate(verts[i][j], verts[i + 1][j], verts[i][j + 1], cell.appearance()));
    }
    return pieces;
  }

  /**
   * Projects a vertex displacement field onto {@code f(u)+g(v)} so every quad is a parallelogram
   * and shared edges can coincide.
   */
  private static double[][] projectParallelograms(double[][] target) {
    int nu = target.length;
    int nv = target[0].length;
    double[] alongU = new double[nu];
    double[] alongV = new double[nv];
    for (int i = 0; i < nu; i++) {
      double sum = 0;
      for (int j = 0; j < nv; j++) {
        sum += target[i][j];
      }
      alongU[i] = sum / nv;
    }
    for (int j = 0; j < nv; j++) {
      double sum = 0;
      for (int i = 0; i < nu; i++) {
        sum += target[i][j] - alongU[i];
      }
      alongV[j] = sum / nu;
    }
    double[][] projected = new double[nu][nv];
    for (int i = 0; i < nu; i++) {
      for (int j = 0; j < nv; j++) {
        projected[i][j] = alongU[i] + alongV[j];
      }
    }
    return projected;
  }

  private static double vertexThin(
      int u, int v, Map<PlaneKey, Double> restThin, double fallback) {
    double sum = 0;
    int n = 0;
    for (int du = -1; du <= 0; du++) {
      for (int dv = -1; dv <= 0; dv++) {
        Double thin = restThin.get(new PlaneKey(u + du, v + dv));
        if (thin != null) {
          sum += thin;
          n++;
        }
      }
    }
    if (n == 0) {
      return fallback;
    }
    return sum / n;
  }

  private static SailPiece affinePlate(
      Vector3d v00, Vector3d v10, Vector3d v01, String appearance) {
    Vector3d eU = new Vector3d(v10).sub(v00);
    Vector3d eV = new Vector3d(v01).sub(v00);
    Vector3d normal = new Vector3d(eU).cross(eV);
    if (normal.lengthSquared() < 1e-16) {
      normal.set(0, 0, 1);
    } else {
      normal.normalize();
    }
    Vector3d xHat = new Vector3d(eU);
    if (xHat.lengthSquared() < 1e-16) {
      xHat.set(1, 0, 0);
      if (Math.abs(xHat.dot(normal)) > 0.9) {
        xHat.set(0, 1, 0);
      }
    }
    xHat.normalize();
    Vector3d yHat = new Vector3d(normal).cross(xHat).normalize();
    Svd2 svd = svd2(eU.length(), eV.dot(xHat), eV.dot(yHat));
    Matrix3d frame = new Matrix3d(xHat, yHat, normal);
    Matrix3d uEmbed =
        new Matrix3d(
            new Vector3d(svd.u00, svd.u10, 0),
            new Vector3d(svd.u01, svd.u11, 0),
            new Vector3d(0, 0, 1));
    Matrix3d vEmbedT =
        new Matrix3d(
            new Vector3d(svd.v00, svd.v01, 0),
            new Vector3d(svd.v10, svd.v11, 0),
            new Vector3d(0, 0, 1));
    Quaterniond left = new Quaterniond().setFromUnnormalized(frame.mul(uEmbed)).normalize();
    Quaterniond right = new Quaterniond().setFromUnnormalized(vEmbedT).normalize();
    return new SailPiece(
        v00.x,
        v00.y,
        v00.z,
        svd.s0,
        svd.s1,
        PLATE_THICKNESS,
        left.x,
        left.y,
        left.z,
        left.w,
        right.x,
        right.y,
        right.z,
        right.w,
        appearance);
  }

  /**
   * SVD of the 2×2 {@code [[a, b], [0, c]]} parallelogram map as rotations and positive scales.
   */
  private static Svd2 svd2(double a, double b, double c) {
    double ata00 = a * a;
    double ata01 = a * b;
    double ata11 = b * b + c * c;
    double trace = ata00 + ata11;
    double disc = Math.sqrt(Math.max(0.0, trace * trace - 4.0 * ata00 * c * c));
    double l0 = 0.5 * (trace + disc);
    double l1 = 0.5 * (trace - disc);
    double v00;
    double v10;
    if (Math.abs(ata01) > 1e-12) {
      v00 = l0 - ata11;
      v10 = ata01;
    } else if (ata00 >= ata11) {
      v00 = 1.0;
      v10 = 0.0;
    } else {
      v00 = 0.0;
      v10 = 1.0;
    }
    double vLen = Math.hypot(v00, v10);
    v00 /= vLen;
    v10 /= vLen;
    double v01 = -v10;
    double v11 = v00;
    double s0 = Math.sqrt(Math.max(l0, 0.0));
    double s1 = Math.sqrt(Math.max(l1, 0.0));
    double u00;
    double u10;
    double u01;
    double u11;
    if (s0 > 1e-12) {
      u00 = (a * v00 + b * v10) / s0;
      u10 = (c * v10) / s0;
    } else {
      u00 = 1.0;
      u10 = 0.0;
    }
    if (s1 > 1e-12) {
      u01 = (a * v01 + b * v11) / s1;
      u11 = (c * v11) / s1;
    } else {
      u01 = -u10;
      u11 = u00;
    }
    double uLen0 = Math.hypot(u00, u10);
    u00 /= uLen0;
    u10 /= uLen0;
    double uLen1 = Math.hypot(u01, u11);
    u01 /= uLen1;
    u11 /= uLen1;
    if (u00 * u11 - u01 * u10 < 0) {
      u01 = -u01;
      u11 = -u11;
      v01 = -v01;
      v11 = -v11;
    }
    return new Svd2(
        u00,
        u01,
        u10,
        u11,
        v00,
        v01,
        v10,
        v11,
        Math.max(s0, PLATE_THICKNESS),
        Math.max(s1, PLATE_THICKNESS));
  }

  private static int planeU(int axis, SailCell cell) {
    if (axis == 0) {
      return cell.y();
    }
    return cell.x();
  }

  private static int planeV(int axis, SailCell cell) {
    if (axis == 2) {
      return cell.y();
    }
    return cell.z();
  }

  private static Vector3d unproject(int axis, double u, double v, double thin) {
    if (axis == 0) {
      return new Vector3d(thin, u, v);
    }
    if (axis == 1) {
      return new Vector3d(u, thin, v);
    }
    return new Vector3d(u, v, thin);
  }

  private static Vector3d centroid(Collection<SailCell> cells) {
    double x = 0;
    double y = 0;
    double z = 0;
    int n = 0;
    for (SailCell cell : cells) {
      x += cell.x() + 0.5;
      y += cell.y() + 0.5;
      z += cell.z() + 0.5;
      n++;
    }
    if (n == 0) {
      return new Vector3d();
    }
    return new Vector3d(x / n, y / n, z / n);
  }

  private static SailPiece plate(SailCell cell, int axis, double originThin) {
    if (axis == 0) {
      return new SailPiece(
          originThin,
          cell.y(),
          cell.z(),
          PLATE_THICKNESS,
          1.0,
          1.0,
          0.0,
          0.0,
          0.0,
          1.0,
          cell.appearance());
    }
    if (axis == 1) {
      return new SailPiece(
          cell.x(),
          originThin,
          cell.z(),
          1.0,
          PLATE_THICKNESS,
          1.0,
          0.0,
          0.0,
          0.0,
          1.0,
          cell.appearance());
    }
    return new SailPiece(
        cell.x(),
        cell.y(),
        originThin,
        1.0,
        1.0,
        PLATE_THICKNESS,
        0.0,
        0.0,
        0.0,
        1.0,
        cell.appearance());
  }

  private static int thinAxis(int extentX, int extentY, int extentZ, String appearance) {
    int min = Math.min(extentX, Math.min(extentY, extentZ));
    boolean xThin = extentX == min;
    boolean yThin = extentY == min;
    boolean zThin = extentZ == min;
    int preferred = facingAxis(appearance);
    if (preferred == 0 && xThin) {
      return 0;
    }
    if (preferred == 1 && yThin) {
      return 1;
    }
    if (preferred == 2 && zThin) {
      return 2;
    }
    if (zThin) {
      return 2;
    }
    if (xThin) {
      return 0;
    }
    return 1;
  }

  private static String materialKey(String blockData) {
    int bracket = blockData.indexOf('[');
    if (bracket < 0) {
      return blockData;
    }
    return blockData.substring(0, bracket);
  }

  private static int facingAxis(String blockData) {
    String facing = facingValue(blockData);
    if ("east".equals(facing) || "west".equals(facing)) {
      return 0;
    }
    if ("up".equals(facing) || "down".equals(facing)) {
      return 1;
    }
    return 2;
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

  /**
   * Integer cell key used for connected-component membership.
   *
   * @param x cell x
   * @param y cell y
   * @param z cell z
   */
  private record CellKey(int x, int y, int z) {}

  /**
   * In-plane grid key for a cloth region.
   *
   * @param u first in-plane axis
   * @param v second in-plane axis
   */
  private record PlaneKey(int u, int v) {}

  /**
   * 2×2 SVD of a parallelogram map.
   *
   * @param u00 left rotation 00
   * @param u01 left rotation 01
   * @param u10 left rotation 10
   * @param u11 left rotation 11
   * @param v00 right rotation 00
   * @param v01 right rotation 01
   * @param v10 right rotation 10
   * @param v11 right rotation 11
   * @param s0 first singular value
   * @param s1 second singular value
   */
  private record Svd2(
      double u00,
      double u01,
      double u10,
      double u11,
      double v00,
      double v01,
      double v10,
      double v11,
      double s0,
      double s1) {}
}
