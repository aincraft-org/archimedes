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
 * Bukkit-backed renderer adapter: creates a non-persistent block display per
 * block carrying the ship's identifier, and removes every tagged display on
 * disassembly through the surface's world query.
 */
public final class BukkitShipRenderer implements ShipRendererLike {
  private final RenderSurface surface;
  private final NamespacedKey shipKey;

  /** Creates the renderer for a surface and namespace key. */
  public BukkitShipRenderer(RenderSurface surface, NamespacedKey shipKey) {
    this.surface = surface;
    this.shipKey = shipKey;
  }

  @Override
  public void render(Ship ship, ShipHolder holder) {
    List<BlockDisplay> displays = new ArrayList<>(ship.blockCount());
    for (var block : ship.blocks()) {
      BlockData data = surface.blockData(block.blockData());
      BlockDisplay display =
          surface.spawnBlockDisplay(
              surface.location(ship.origin(), block.pos().x(), block.pos().y(), block.pos().z()),
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

  @Override
  public void removeRuntime(Ship ship) {
    surface.removeTagged(shipKey, ship.id().toString());
  }
}
