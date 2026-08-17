package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.model.Ship;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipTransform;
import dev.mintychochip.archimedes.render.RenderSurface;
import dev.mintychochip.archimedes.ship.ShipHolder;
import dev.mintychochip.archimedes.ship.ShipRendererLike;
import dev.mintychochip.archimedes.ship.ShipRuntimeException;
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
@SuppressWarnings({
  "checkstyle:IllegalCatch",
  "PMD.AvoidCatchingGenericException",
  "PMD.AvoidDuplicateLiterals"
})
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
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
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

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private void cleanupRender(Ship ship, ShipRuntimeException failure) {
    try {
      surface.removeTagged(shipKey, ship.id().toString());
    } catch (RuntimeException cleanup) {
      failure.addSuppressed(cleanup);
    }
    throw failure;
  }

  /**
   * Removes every tagged display for the ship.
   *
   * @param ship the ship to clean up
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public void removeRuntime(Ship ship) {
    try {
      surface.removeTagged(shipKey, ship.id().toString());
    } catch (RuntimeException failure) {
      if (failure instanceof ShipRuntimeException) {
        throw (ShipRuntimeException) failure;
      }
      throw new ShipRuntimeException("Renderer removal failed for ship " + ship.id(), failure);
    }
  }

  @Override
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public void removeAllRuntime() {
    try {
      surface.removeAllTagged(shipKey);
    } catch (RuntimeException failure) {
      if (failure instanceof ShipRuntimeException) {
        throw (ShipRuntimeException) failure;
      }
      throw new ShipRuntimeException("Renderer tagged removal failed", failure);
    }
  }

  /**
   * Teleports every tagged display to model-derived positions.
   *
   * @param ship the ship to reposition
   * @param oldY the previous pose y
   * @param newY the new pose y
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public void reposition(Ship ship, double oldY, double newY) {
    try {
      Map<BlockDisplay, ShipBlock> blocksByDisplay = pairDisplays(ship);
      for (Map.Entry<BlockDisplay, ShipBlock> entry : blocksByDisplay.entrySet()) {
        surface.teleport(entry.getKey(), location(ship, entry.getValue()));
      }
    } catch (ShipRuntimeException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ShipRuntimeException("Renderer reposition failed for ship " + ship.id(), failure);
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
