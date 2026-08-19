package dev.mintychochip.archimedes.sail;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Predetermined demo sail hulls in a few sizes. Default is {@link Size#MEDIUM} with a flat wall.
 *
 * <p>Used by {@code /arch sail}. Callers choose the world origin, size, and optional {@link
 * Shape#MESH} cloth volume.
 */
public final class SailShipTemplate {
  /** Deck appearance. */
  public static final String DECK = "minecraft:oak_planks";

  /** Mast appearance. */
  public static final String MAST = "minecraft:oak_log";

  /** Sail appearance (tessellated by {@link SailMesh}). */
  public static final String SAIL = "minecraft:white_wool";

  /**
   * Named hull sizes. Sail area is {@code sailSpan × sailHeight} wool blocks; each block is 1 m² of
   * pressure sail.
   */
  public enum Size {
    /** 3×3 deck, 3×3 sail. */
    SMALL(3, 3, 4),
    /** 5×5 deck, 5×5 sail. */
    MEDIUM(5, 5, 7),
    /** 7×7 deck, 7×7 sail. */
    LARGE(7, 7, 10);

    /** Odd deck width. */
    private final int deckSpan;

    /** Sail width and height in blocks. */
    private final int sailSpan;

    /** Mast height above the deck. */
    private final int mastHeight;

    Size(int deckSpan, int sailSpan, int mastHeight) {
      this.deckSpan = deckSpan;
      this.sailSpan = sailSpan;
      this.mastHeight = mastHeight;
    }

    /**
     * @return odd deck width
     */
    public int deckSpan() {
      return deckSpan;
    }

    /**
     * @return sail width and height
     */
    public int sailSpan() {
      return sailSpan;
    }

    /**
     * @return mast height above the deck
     */
    public int mastHeight() {
      return mastHeight;
    }

    /**
     * Parses a size name; unknown values are {@code null}.
     *
     * @param raw player argument
     * @return matching size, or {@code null}
     */
    public static Size parse(String raw) {
      if (raw == null) {
        return null;
      }
      try {
        return Size.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
  }

  /** Cloth layout: a one-cell wall or a multi-depth mesh volume. */
  public enum Shape {
    /** One-cell-thick wool wall on +Z of the mast. */
    FLAT,
    /** Wool occupies more than one depth so tessellation is a 3D plate mesh. */
    MESH;

    /**
     * Parses a shape name; unknown values are {@code null}.
     *
     * @param raw player argument
     * @return matching shape, or {@code null}
     */
    public static Shape parse(String raw) {
      if (raw == null) {
        return null;
      }
      try {
        return Shape.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
  }

  /**
   * Parsed {@code /arch sail} size and shape.
   *
   * @param size named hull size
   * @param shape cloth layout
   */
  public record Spec(Size size, Shape shape) {
    /** Token that selects a medium mesh sail. */
    private static final String MESH_TOKEN = "mesh";

    /** Suffix that selects mesh on a named size ({@code large-mesh}). */
    private static final String MESH_SUFFIX = "-mesh";

    /**
     * @param size named hull size
     * @param shape cloth layout
     */
    public Spec {
      Objects.requireNonNull(size, "size");
      Objects.requireNonNull(shape, "shape");
    }

    /**
     * Parses {@code small}/{@code medium}/{@code large}, {@code mesh}, or {@code <size>-mesh}.
     *
     * @param raw player argument
     * @return matching spec, or {@code null}
     */
    public static Spec parse(String raw) {
      if (raw == null) {
        return null;
      }
      String token = raw.trim().toLowerCase(Locale.ROOT);
      if (token.isEmpty()) {
        return null;
      }
      if (MESH_TOKEN.equals(token)) {
        return new Spec(Size.MEDIUM, Shape.MESH);
      }
      if (token.endsWith(MESH_SUFFIX)) {
        Size size = Size.parse(token.substring(0, token.length() - MESH_SUFFIX.length()));
        if (size == null) {
          return null;
        }
        return new Spec(size, Shape.MESH);
      }
      Size size = Size.parse(token);
      if (size == null) {
        return null;
      }
      return new Spec(size, Shape.FLAT);
    }
  }

  private SailShipTemplate() {}

  /**
   * @return a medium hull
   */
  public static List<ShipBlock> blocks() {
    return blocks(Size.MEDIUM);
  }

  /**
   * Builds a hull of the given size.
   *
   * @param size named size
   * @return immutable relative block list
   */
  public static List<ShipBlock> blocks(Size size) {
    return blocks(size, Shape.FLAT);
  }

  /**
   * Builds a hull from a parsed size/shape spec.
   *
   * @param spec named size and cloth layout
   * @return immutable relative block list
   */
  public static List<ShipBlock> blocks(Spec spec) {
    Objects.requireNonNull(spec, "spec");
    return blocks(spec.size(), spec.shape());
  }

  /**
   * Builds a hull of the given size and cloth layout.
   *
   * @param size named size
   * @param shape cloth layout
   * @return immutable relative block list
   */
  public static List<ShipBlock> blocks(Size size, Shape shape) {
    Objects.requireNonNull(size, "size");
    Objects.requireNonNull(shape, "shape");
    int deckHalf = size.deckSpan() / 2;
    int sailHalf = size.sailSpan() / 2;
    List<ShipBlock> blocks = new ArrayList<>();
    for (int x = -deckHalf; x <= deckHalf; x++) {
      for (int z = -deckHalf; z <= deckHalf; z++) {
        blocks.add(new ShipBlock(new BlockPos(x, 0, z), DECK));
      }
    }
    for (int y = 1; y <= size.mastHeight(); y++) {
      blocks.add(new ShipBlock(new BlockPos(0, y, 0), MAST));
    }
    int sailMinY = size.mastHeight() - size.sailSpan() + 1;
    int maxZ = shape == Shape.MESH ? 2 : 1;
    for (int x = -sailHalf; x <= sailHalf; x++) {
      for (int y = sailMinY; y <= size.mastHeight(); y++) {
        for (int z = 1; z <= maxZ; z++) {
          blocks.add(new ShipBlock(new BlockPos(x, y, z), SAIL));
        }
      }
    }
    return List.copyOf(blocks);
  }
}
