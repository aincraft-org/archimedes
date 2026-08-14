package dev.jlo.ships.bukkit;

import dev.jlo.ships.model.Ship;
import dev.jlo.ships.render.RenderSurface;
import dev.jlo.ships.ship.ShipHolder;
import dev.jlo.ships.ship.ShipRendererLike;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bukkit-backed renderer adapter: creates a non-persistent block display per block carrying the
 * ship's identifier, and removes every tagged display on disassembly through the surface's world
 * query.
 */
public final class BukkitShipRenderer implements ShipRendererLike {
  /** The rendering surface. */
  private final RenderSurface surface;

  /** Tag key carrying the ship identifier. */
  private final NamespacedKey shipKey;

  /**
   * Creates the renderer for a surface and namespace key.
   *
   * @param surface the rendering surface
   * @param shipKey the ship identifier tag key
   */
  public BukkitShipRenderer(RenderSurface surface, NamespacedKey shipKey) {
    this.surface = surface;
    this.shipKey = shipKey;
  }

  /**
   * Renders the ship as tagged non-persistent block displays.
   *
   * @param ship the ship to render
   * @param holder the finalization receiver
   */
  @Override
  public void render(Ship ship, ShipHolder holder) {
    List<BlockDisplay> displays = new ArrayList<>(ship.blockCount());
    for (var block : ship.blocks()) {
      BlockData data = surface.blockData(block.blockData());
      BlockDisplay display =
          surface.spawnBlockDisplay(
              surface.location(
                  ship.origin(),
                  block.pos().x(),
                  ship.pose().y() + block.pos().y(),
                  block.pos().z()),
              d -> {
                d.setBlock(data);
                d.setPersistent(false);
                d.getPersistentDataContainer()
                    .set(shipKey, PersistentDataType.STRING, ship.id().toString());
              });
      displays.add(display);
    }
    surface.shipRendered(ship.id(), displays);
    holder.accept(ship);
  }

  /**
   * Removes every tagged display for the ship.
   *
   * @param ship the ship to clean up
   */
  @Override
  public void removeRuntime(Ship ship) {
    surface.removeTagged(shipKey, ship.id().toString());
  }

  /**
   * Teleports every tagged display to the new pose, preserving relative offsets.
   *
   * @param ship the ship to reposition
   * @param oldY the previous pose y
   * @param newY the new pose y
   */
  @Override
  public void reposition(Ship ship, double oldY, double newY) {
    for (BlockDisplay display : surface.tagged(shipKey, ship.id().toString())) {
      org.bukkit.Location loc = display.getLocation();
      double relX = loc.getX() - (ship.origin().x() + 0.5);
      double relY = loc.getY() - (ship.origin().y() + oldY + 0.5);
      double relZ = loc.getZ() - (ship.origin().z() + 0.5);
      surface.teleport(display, surface.location(ship.origin(), relX, newY + relY, relZ));
    }
  }
}
