package dev.mintychochip.archimedes.render;

import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipTransform;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.sail.SailMesh;
import dev.mintychochip.archimedes.sail.SailPiece;
import dev.mintychochip.phys.FlowField;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;

/**
 * Creates one non-persistent block display per hull block and a tessellated sheet of transformed
 * plates for cloth, then reports the rendered group to the surface.
 */
public final class ShipRenderer {
  /**
   * Client interpolation window for visual teleports, in ticks. One physics tick so the picture
   * does not lag a full step behind the collision hull.
   */
  public static final int TELEPORT_DURATION_TICKS = 1;

  /** Wind sampled when tessellating sail plates. */
  private final FlowField wind;

  /** Still-air renderer. */
  public ShipRenderer() {
    this(FlowField.still());
  }

  /**
   * @param wind flow field sampled for cloth billow
   */
  public ShipRenderer(FlowField wind) {
    this.wind = Objects.requireNonNull(wind, "wind");
  }

  /**
   * Renders hull blocks as untransformed displays and cloth as tessellated plates.
   *
   * @param ship the ship to render
   * @param surface the rendering surface
   */
  public void render(Vehicle ship, RenderSurface surface) {
    List<BlockDisplay> displays = new ArrayList<>(ship.blockCount());
    for (ShipBlock block : ship.blocks()) {
      if (SailMesh.isCloth(block.blockData())) {
        continue;
      }
      ShipTransform.VisualPosition position = ShipTransform.visual(ship, block.pos());
      Location location =
          surface.location(
              ship.origin(),
              position.x() - ship.origin().x(),
              position.y() - ship.origin().y(),
              position.z() - ship.origin().z());
      BlockData data = surface.blockData(block.blockData());
      BlockDisplay display =
          surface.spawnBlockDisplay(
              location,
              d -> {
                d.setBlock(data);
                d.setTeleportDuration(TELEPORT_DURATION_TICKS);
                d.setVisibleByDefault(false);
              });
      display.setPersistent(false);
      displays.add(display);
    }
    for (SailPiece piece : SailMesh.tessellate(SailMesh.cellsOf(ship.intactBlocks()), wind)) {
      BlockData data = surface.blockData(SailMesh.worldData(piece.appearance()));
      BlockDisplay display =
          surface.spawnBlockDisplay(
              SailTransform.location(surface, ship, piece),
              d -> {
                d.setBlock(data);
                d.setTransformation(SailTransform.transformation(piece));
                d.setTeleportDuration(TELEPORT_DURATION_TICKS);
                d.setVisibleByDefault(false);
              });
      display.setPersistent(false);
      displays.add(display);
    }
    surface.shipRendered(ship.id(), displays);
  }
}
