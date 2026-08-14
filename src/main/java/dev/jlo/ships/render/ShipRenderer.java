package dev.jlo.ships.render;

import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;

/**
 * Creates one non-persistent block display per ship block at an adjusted
 * position and reports the rendered group to the surface.
 */
public final class ShipRenderer {
  /** Renders all ship blocks as block displays on the surface. */
  public void render(Ship ship, RenderSurface surface) {
    List<BlockDisplay> displays = new ArrayList<>(ship.blockCount());
    for (ShipBlock block : ship.blocks()) {
      Location location = surface.location(ship.origin(), block.pos().x(), block.pos().y(), block.pos().z());
      BlockData data = surface.blockData(block.blockData());
      BlockDisplay display = surface.spawnBlockDisplay(location, d -> d.setBlock(data));
      display.setPersistent(false);
      displays.add(display);
    }
    surface.shipRendered(ship.id(), displays);
  }
}