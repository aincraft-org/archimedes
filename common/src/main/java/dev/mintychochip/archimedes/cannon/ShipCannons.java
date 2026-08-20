package dev.mintychochip.archimedes.cannon;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.Vehicle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Discovers cannon mounts deterministically from captured ship blocks. */
@SuppressWarnings({"checkstyle:JavadocVariable", "PMD.AvoidDuplicateLiterals"})
public final class ShipCannons {
  private static final List<CannonDirection> DIRECTIONS = List.of(CannonDirection.values());

  private ShipCannons() {}

  public static List<CannonMount> discover(Vehicle ship) {
    Map<BlockPos, ShipBlock> blocks = new HashMap<>();
    for (ShipBlock block : ship.blocks()) {
      blocks.put(block.pos(), block);
    }
    List<CannonMount> mounts = new ArrayList<>();
    for (ShipBlock block : ship.blocks()) {
      if (!material(block.blockData()).equals("minecraft:dispenser")) {
        continue;
      }
      CannonDirection direction = CannonDirection.parse(property(block.blockData(), "facing"));
      if (direction == null) {
        continue;
      }
      List<BlockPos> controls = new ArrayList<>();
      for (CannonDirection offset : DIRECTIONS) {
        BlockPos candidate = offset(block.pos(), offset);
        ShipBlock button = blocks.get(candidate);
        if (button != null && attachedButton(button, offset)) {
          controls.add(candidate);
        }
      }
      if (controls.size() == 1) {
        mounts.add(new CannonMount(block.pos(), controls.get(0), direction));
      }
    }
    mounts.sort(
        Comparator.comparingInt((CannonMount mount) -> mount.dispenser().x())
            .thenComparingInt(mount -> mount.dispenser().y())
            .thenComparingInt(mount -> mount.dispenser().z()));
    return List.copyOf(mounts);
  }

  public static Optional<CannonMount> atControl(Vehicle ship, BlockPos control) {
    return discover(ship).stream().filter(mount -> mount.control().equals(control)).findFirst();
  }

  private static boolean attachedButton(ShipBlock button, CannonDirection fromDispenser) {
    if (!material(button.blockData()).equals("minecraft:stone_button")) {
      return false;
    }
    String face = property(button.blockData(), "face");
    String facing = property(button.blockData(), "facing");
    return switch (fromDispenser) {
      case UP -> face.equals("floor");
      case DOWN -> face.equals("ceiling");
      case NORTH -> face.equals("wall") && facing.equals("north");
      case SOUTH -> face.equals("wall") && facing.equals("south");
      case EAST -> face.equals("wall") && facing.equals("east");
      case WEST -> face.equals("wall") && facing.equals("west");
    };
  }

  private static BlockPos offset(BlockPos pos, CannonDirection direction) {
    return new BlockPos(
        pos.x() + direction.dx(), pos.y() + direction.dy(), pos.z() + direction.dz());
  }

  private static String material(String data) {
    int properties = data.indexOf('[');
    return properties < 0 ? data : data.substring(0, properties);
  }

  private static String property(String data, String name) {
    int properties = data.indexOf('[');
    if (properties < 0 || !data.endsWith("]")) {
      return "";
    }
    for (String entry : data.substring(properties + 1, data.length() - 1).split(",")) {
      int separator = entry.indexOf('=');
      if (separator > 0 && entry.substring(0, separator).equals(name)) {
        return entry.substring(separator + 1);
      }
    }
    return "";
  }
}
