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
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Turns a 3D region of cloth cells into a series of thin BlockDisplay plates.
 *
 * <p>Connected cells form one region. A 1-cell-thick wall stays a sheet along its thinnest axis. A
 * multi-depth region emits one plate per cell at that cell's own depth so the union occupies 3D
 * space. A sampled wind billows plate origins and tilts them; still air leaves the rest pose.
 * Geometry is computed from the region; no item model or resource pack is involved.
 */
public final class SailMesh {
  /**
   * Plate thickness in blocks: one Minecraft pixel. The fitted sheet matches the region AABB on the
   * two in-plane axes and is this thick on the remaining axis.
   */
  public static final double PLATE_THICKNESS = 1.0 / 16.0;

  /** Wind speed (m/s) that produces a full belly offset. Matches the plugin default breeze. */
  private static final double BILLOW_REF_SPEED = 8.0;

  /** Maximum plate offset along the wind, in blocks. */
  private static final double BILLOW_DEPTH = 0.35;

  /** Maximum plate tilt toward the wind, in radians. */
  private static final double BILLOW_TILT = Math.toRadians(20.0);

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
      pieces.addAll(sheet(flood(seed, byKey, remaining)));
    }
    return List.copyOf(billow(pieces, apparentWind));
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

  private static List<SailPiece> sheet(List<SailCell> region) {
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
    List<SailPiece> pieces = new ArrayList<>(region.size());
    for (SailCell cell : region) {
      pieces.add(plate(cell, axis, thinOrigin(cell, axis)));
    }
    return pieces;
  }

  private static double thinOrigin(SailCell cell, int axis) {
    int coord = axis == 0 ? cell.x() : axis == 1 ? cell.y() : cell.z();
    return coord + 0.5 - PLATE_THICKNESS * 0.5;
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

  private static List<SailPiece> billow(List<SailPiece> pieces, Vector3dc apparentWind) {
    double speed = apparentWind.length();
    if (speed < 1e-9) {
      return pieces;
    }
    double strength = Math.min(speed / BILLOW_REF_SPEED, 1.0);
    double nx = apparentWind.x() / speed;
    double ny = apparentWind.y() / speed;
    double nz = apparentWind.z() / speed;
    double dx = nx * strength * BILLOW_DEPTH;
    double dy = ny * strength * BILLOW_DEPTH;
    double dz = nz * strength * BILLOW_DEPTH;
    Vector3d windDir = new Vector3d(nx, ny, nz);
    List<SailPiece> billowed = new ArrayList<>(pieces.size());
    for (SailPiece piece : pieces) {
      Quaterniond rot = tilt(piece, windDir, strength);
      billowed.add(
          new SailPiece(
              piece.originX() + dx,
              piece.originY() + dy,
              piece.originZ() + dz,
              piece.scaleX(),
              piece.scaleY(),
              piece.scaleZ(),
              rot.x,
              rot.y,
              rot.z,
              rot.w,
              piece.appearance()));
    }
    return billowed;
  }

  private static Quaterniond tilt(SailPiece piece, Vector3dc windDir, double strength) {
    Vector3d normal = plateNormal(piece);
    Vector3d axis = new Vector3d(normal).cross(windDir);
    if (axis.lengthSquared() < 1e-12) {
      return new Quaterniond(piece.rotX(), piece.rotY(), piece.rotZ(), piece.rotW());
    }
    axis.normalize();
    Quaterniond extra = new Quaterniond().fromAxisAngleRad(axis, strength * BILLOW_TILT);
    return extra.mul(new Quaterniond(piece.rotX(), piece.rotY(), piece.rotZ(), piece.rotW()));
  }

  private static Vector3d plateNormal(SailPiece piece) {
    double ax = Math.abs(piece.scaleX() - PLATE_THICKNESS);
    double ay = Math.abs(piece.scaleY() - PLATE_THICKNESS);
    double az = Math.abs(piece.scaleZ() - PLATE_THICKNESS);
    if (ax <= ay && ax <= az) {
      return new Vector3d(1, 0, 0);
    }
    if (ay <= az) {
      return new Vector3d(0, 1, 0);
    }
    return new Vector3d(0, 0, 1);
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
}
