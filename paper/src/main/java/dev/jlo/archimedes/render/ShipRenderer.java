package dev.jlo.archimedes.render;

import dev.jlo.archimedes.model.Ship;
import dev.jlo.archimedes.model.ShipBlock;
import dev.jlo.archimedes.model.ShipTransform;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;

/**
 * Creates one non-persistent block display per ship block at an adjusted position and reports the
 * rendered group to the surface.
 */
public final class ShipRenderer {
  /**
   * Renders all ship blocks as block displays on the surface.
   *
   * @param ship the ship to render
   * @param surface the rendering surface
   */
  public void render(Ship ship, RenderSurface surface) {
    List<BlockDisplay> displays = new ArrayList<>(ship.blockCount());
    for (ShipBlock block : ship.blocks()) {
      ShipTransform.VisualPosition position = ShipTransform.visual(ship, block.pos());
      Location location =
          surface.location(
              ship.origin(),
              position.x() - ship.origin().x(),
              position.y() - ship.origin().y(),
              position.z() - ship.origin().z());
      BlockData data = surface.blockData(block.blockData());
      BlockDisplay display = surface.spawnBlockDisplay(location, d -> d.setBlock(data));
      display.setPersistent(false);
      displays.add(display);
    }
    surface.shipRendered(ship.id(), displays);
  }
}
