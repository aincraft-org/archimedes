package dev.mintychochip.archimedes.cannon;

/** Unit direction of a captured dispenser cannon. */
@SuppressWarnings({"checkstyle:JavadocVariable", "checkstyle:EmptyLineSeparator"})
public enum CannonDirection {
  NORTH(0, 0, -1),
  SOUTH(0, 0, 1),
  EAST(1, 0, 0),
  WEST(-1, 0, 0),
  UP(0, 1, 0),
  DOWN(0, -1, 0);

  private final int dx;
  private final int dy;
  private final int dz;

  CannonDirection(int dx, int dy, int dz) {
    this.dx = dx;
    this.dy = dy;
    this.dz = dz;
  }

  public int dx() {
    return dx;
  }

  public int dy() {
    return dy;
  }

  public int dz() {
    return dz;
  }

  static CannonDirection parse(String value) {
    return switch (value) {
      case "north" -> NORTH;
      case "south" -> SOUTH;
      case "east" -> EAST;
      case "west" -> WEST;
      case "up" -> UP;
      case "down" -> DOWN;
      default -> null;
    };
  }
}
