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
    Vector3d[][] verts = new Vector3d[spanU + 1][spanV + 1];
    for (int i = 0; i <= spanU; i++) {
      for (int j = 0; j <= spanV; j++) {
        int u = minU + i;
        int v = minV + j;
        double thin = vertexThin(u, v, restThin, fallbackThin);
        double uNorm = spanU == 0 ? 0.5 : i / (double) spanU;
        double vNorm = spanV == 0 ? 0.5 : j / (double) spanV;
        double fromMast = Math.abs(uNorm - 0.5) * 2.0;
        double hoist = Math.sin(Math.PI * vNorm);
        double cup = (0.15 + 0.85 * fromMast * fromMast) * (0.15 + 0.85 * hoist);
        Vector3d rest = unproject(axis, u, v, thin);
        verts[i][j] =
            new Vector3d(
                rest.x + windDir.x * strength * cup,
                rest.y + windDir.y * strength * cup,
                rest.z + windDir.z * strength * cup);
      }
    }
    Vector3d restNormal = restNormal(axis);
    List<SailPiece> pieces = new ArrayList<>(plane.size());
    for (SailCell cell : plane.values()) {
      int i = planeU(axis, cell) - minU;
      int j = planeV(axis, cell) - minV;
      pieces.add(
          plateFromQuad(
              verts[i][j],
              verts[i + 1][j],
              verts[i][j + 1],
              restNormal,
              cell.appearance()));
    }
    return pieces;
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

  private static SailPiece plateFromQuad(
      Vector3d v00, Vector3d v10, Vector3d v01, Vector3dc restNormal, String appearance) {
    Vector3d eU = new Vector3d(v10).sub(v00);
    Vector3d eV = new Vector3d(v01).sub(v00);
    Vector3d normal = new Vector3d(eU).cross(eV);
    if (normal.lengthSquared() < 1e-12) {
      normal.set(restNormal);
    } else {
      normal.normalize();
      if (normal.dot(restNormal) < 0) {
        normal.negate();
      }
    }
    Vector3d xAxis = tangentAxis(eU, normal);
    Vector3d yAxis = new Vector3d(normal).cross(xAxis).normalize();
    if (yAxis.dot(eV) < 0) {
      yAxis.negate();
      normal.negate();
    }
    double sx = Math.max(eU.length(), PLATE_THICKNESS);
    double sy = Math.max(Math.abs(yAxis.dot(eV)), PLATE_THICKNESS);
    Quaterniond rot = rotationFromAxes(xAxis, yAxis, normal);
    double half = PLATE_THICKNESS * 0.5;
    Vector3d origin = new Vector3d(v00).sub(normal.x * half, normal.y * half, normal.z * half);
    return new SailPiece(
        origin.x,
        origin.y,
        origin.z,
        sx,
        sy,
        PLATE_THICKNESS,
        rot.x,
        rot.y,
        rot.z,
        rot.w,
        appearance);
  }

  private static Vector3d tangentAxis(Vector3d edge, Vector3dc normal) {
    Vector3d axis = new Vector3d(edge);
    double along = axis.dot(normal);
    axis.sub(normal.x() * along, normal.y() * along, normal.z() * along);
    if (axis.lengthSquared() < 1e-12) {
      axis.set(1, 0, 0);
      if (Math.abs(axis.dot(normal)) > 0.9) {
        axis.set(0, 1, 0);
      }
      along = axis.dot(normal);
      axis.sub(normal.x() * along, normal.y() * along, normal.z() * along);
    }
    return axis.normalize();
  }

  private static Quaterniond rotationFromAxes(Vector3dc x, Vector3dc y, Vector3dc z) {
    return new Quaterniond().setFromUnnormalized(new Matrix3d(x, y, z)).normalize();
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

  private static Vector3d restNormal(int axis) {
    if (axis == 0) {
      return new Vector3d(1, 0, 0);
    }
    if (axis == 1) {
      return new Vector3d(0, 1, 0);
    }
    return new Vector3d(0, 0, 1);
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
}
