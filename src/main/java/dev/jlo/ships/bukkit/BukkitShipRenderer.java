package dev.jlo.ships.bukkit;

import dev.jlo.ships.model.Ship;
import dev.jlo.ships.model.ShipBlock;
import dev.jlo.ships.model.ShipTransform;
import dev.jlo.ships.render.RenderSurface;
import dev.jlo.ships.ship.ShipHolder;
import dev.jlo.ships.ship.ShipRendererLike;
import dev.jlo.ships.ship.ShipRuntimeException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bukkit-backed renderer adapter: creates a non-persistent block display per block carrying the
 * ship's identifier and stable relative block position, and removes every tagged display on
 * disassembly through the surface's world query.
 */
public final class BukkitShipRenderer implements ShipRendererLike {
  /** The rendering surface. */
  private final RenderSurface surface;

  /** Tag key carrying the ship identifier. */
  private final NamespacedKey shipKey;

  /** Tag key carrying each block's stable relative position. */
  private final NamespacedKey blockKey;

  /**
   * Creates the renderer for a surface and namespace key.
   *
   * @param surface the rendering surface
   * @param shipKey the ship identifier tag key
   */
  public BukkitShipRenderer(RenderSurface surface, NamespacedKey shipKey) {
    this.surface = surface;
    this.shipKey = shipKey;
    this.blockKey = new NamespacedKey(shipKey.getNamespace(), shipKey.getKey() + "-block");
  }

  /**
   * Renders the ship as tagged non-persistent block displays.
   *
   * @param ship the ship to render
   * @param holder the finalization receiver
   */
  public void render(Ship ship, ShipHolder holder) {
    List<BlockDisplay> displays = new ArrayList<>(ship.blockCount());
    try {
      renderDisplays(ship, displays);
      surface.shipRendered(ship.id(), displays);
      holder.accept(ship);
    } catch (RuntimeException failure) {
      ShipRuntimeException normalized =
          failure instanceof ShipRuntimeException
              ? (ShipRuntimeException) failure
              : new ShipRuntimeException("Bukkit render failed for ship " + ship.id(), failure);
      cleanupRender(ship, normalized);
    }
  }

  private void renderDisplays(Ship ship, List<BlockDisplay> displays) {
    for (ShipBlock block : ship.blocks()) {
      BlockData data = surface.blockData(block.blockData());
      BlockDisplay display =
          surface.spawnBlockDisplay(
              location(ship, block),
              d -> {
                d.setBlock(data);
                d.setPersistent(false);
                d.getPersistentDataContainer()
                    .set(shipKey, PersistentDataType.STRING, ship.id().toString());
                d.getPersistentDataContainer().set(blockKey, PersistentDataType.STRING, key(block));
              });
      displays.add(display);
    }
  }

  private void cleanupRender(Ship ship, ShipRuntimeException failure) {
    try {
      surface.removeTagged(shipKey, ship.id().toString());
    } catch (ShipRuntimeException cleanup) {
      failure.addSuppressed(cleanup);
    }
    throw failure;
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

  /** Removes all plugin-owned displays, including stale entities. */
  public void removeAllRuntime() {
    surface.removeAllTagged(shipKey);
  }

  /**
   * Teleports every tagged display to model-derived positions.
   *
   * @param ship the ship to reposition
   * @param oldY the previous pose y
   * @param newY the new pose y
   */
  @Override
  public void reposition(Ship ship, double oldY, double newY) {
    Map<BlockDisplay, ShipBlock> blocksByDisplay = pairDisplays(ship);
    for (Map.Entry<BlockDisplay, ShipBlock> entry : blocksByDisplay.entrySet()) {
      surface.teleport(entry.getKey(), location(ship, entry.getValue()));
    }
  }

  private org.bukkit.Location location(Ship ship, ShipBlock block) {
    ShipTransform.VisualPosition position = ShipTransform.visual(ship, block.pos());
    return surface.location(
        ship.origin(),
        position.x() - ship.origin().x(),
        position.y() - ship.origin().y(),
        position.z() - ship.origin().z());
  }

  private Map<BlockDisplay, ShipBlock> pairDisplays(Ship ship) {
    Map<String, ShipBlock> blocksByKey = new java.util.HashMap<>();
    for (ShipBlock block : ship.blocks()) {
      blocksByKey.put(key(block), block);
    }
    Map<BlockDisplay, ShipBlock> paired = new IdentityHashMap<>();
    for (BlockDisplay display : surface.tagged(shipKey, ship.id().toString())) {
      String blockKeyValue =
          display.getPersistentDataContainer().get(blockKey, PersistentDataType.STRING);
      ShipBlock block = blocksByKey.get(blockKeyValue);
      if (block != null) {
        paired.put(display, block);
      }
    }
    return paired;
  }

  private static String key(ShipBlock block) {
    return block.pos().x() + "," + block.pos().y() + "," + block.pos().z();
  }
}
