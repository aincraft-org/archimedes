package dev.mintychochip.archimedes.bukkit;

import dev.mintychochip.archimedes.model.BlockPos;
import dev.mintychochip.archimedes.model.ShipBlock;
import dev.mintychochip.archimedes.model.ShipTransform;
import dev.mintychochip.archimedes.model.Vehicle;
import dev.mintychochip.archimedes.render.DisplayViewerSet;
import dev.mintychochip.archimedes.render.RenderSurface;
import dev.mintychochip.archimedes.render.SailTransform;
import dev.mintychochip.archimedes.render.ShipRenderer;
import dev.mintychochip.archimedes.sail.SailMesh;
import dev.mintychochip.archimedes.sail.SailPiece;
import dev.mintychochip.archimedes.ship.ShipHolder;
import dev.mintychochip.archimedes.ship.ShipRendererLike;
import dev.mintychochip.archimedes.ship.ShipRuntimeException;
import dev.mintychochip.phys.FlowField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Bukkit-backed renderer adapter: creates a non-persistent block display per hull block carrying
 * the ship's identifier and stable relative block position, plus a separate tagged set of
 * tessellated sail plates. Removes every tagged display on disassembly through the surface's world
 * query.
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

  /** Tag key carrying each sail plate's tessellation index. */
  private final NamespacedKey sailKey;

  /** Tag key carrying each cannon control's relative position. */
  private final NamespacedKey cannonControlKey;

  /** Torn cloth ragdoll displays keyed by debris id. */
  private final Map<UUID, Ragdoll> ragdolls = new HashMap<>();

  /** Wind sampled when tessellating sail plates. */
  private final FlowField wind;

  /**
   * Creates the renderer for a surface and namespace key with still air.
   *
   * @param surface the rendering surface
   * @param shipKey the ship identifier tag key
   */
  public BukkitShipRenderer(RenderSurface surface, NamespacedKey shipKey) {
    this(surface, shipKey, FlowField.still());
  }

  /**
   * Creates the renderer for a surface, namespace key, and wind used to billow sail plates.
   *
   * @param surface the rendering surface
   * @param shipKey the ship identifier tag key
   * @param wind flow field sampled for cloth billow
   */
  public BukkitShipRenderer(RenderSurface surface, NamespacedKey shipKey, FlowField wind) {
    this.surface = surface;
    this.shipKey = shipKey;
    this.blockKey = new NamespacedKey(shipKey.getNamespace(), shipKey.getKey() + "-block");
    this.sailKey = new NamespacedKey(shipKey.getNamespace(), shipKey.getKey() + "-sail");
    this.wind = Objects.requireNonNull(wind, "wind");
    this.cannonControlKey =
        new NamespacedKey(shipKey.getNamespace(), shipKey.getKey() + "-cannon-control");
  }

  /**
   * Returns the stable relative-block identity key used by hull displays.
   *
   * @return renderer block identity key
   */
  public NamespacedKey blockKey() {
    return blockKey;
  }

  /**
   * Returns the identity key used by cannon interaction hitboxes.
   *
   * @return cannon control identity key
   */
  public NamespacedKey cannonControlKey() {
    return cannonControlKey;
  }

  /**
   * A spawned cloth ragdoll display.
   *
   * @param shipId parent vehicle
   * @param display block display
   */
  private record Ragdoll(UUID shipId, BlockDisplay display) {}

  /**
   * Renders the ship as tagged non-persistent block displays. If rendering or registration fails,
   * already-created displays are removed before the failure is propagated.
   *
   * @param ship the ship to render
   * @param holder finalization receiver invoked only after registration succeeds
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public void render(Vehicle ship, ShipHolder holder) {
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

  private void renderDisplays(Vehicle ship, List<BlockDisplay> displays) {
    for (ShipBlock block : ship.blocks()) {
      if (SailMesh.isCloth(block.blockData())) {
        continue;
      }
      BlockData data = surface.blockData(block.blockData());
      BlockDisplay display =
          surface.spawnBlockDisplay(
              location(ship, block),
              d -> {
                d.setBlock(data);
                d.setPersistent(false);
                d.setTeleportDuration(ShipRenderer.TELEPORT_DURATION_TICKS);
                d.setVisibleByDefault(false);
                d.getPersistentDataContainer()
                    .set(shipKey, PersistentDataType.STRING, ship.id().toString());
                d.getPersistentDataContainer().set(blockKey, PersistentDataType.STRING, key(block));
              });
      displays.add(display);
    }
    List<SailPiece> pieces = plates(ship);
    for (int i = 0; i < pieces.size(); i++) {
      displays.add(spawnSail(ship, pieces.get(i), i));
    }
    spawnCannonControls(ship);
    cullViewers(ship);
  }

  /**
   * Shows each hull and sail display only to viewers with line of sight to that cell.
   *
   * @param ship ship whose displays are culled
   */
  public void cullViewers(Vehicle ship) {
    Set<BlockPos> occupied = new HashSet<>();
    for (ShipBlock block : ship.blocks()) {
      occupied.add(ShipTransform.cell(ship, block.pos()));
    }
    Map<BlockDisplay, BlockPos> cells = displayWorldCells(ship);
    Set<BlockPos> candidates = new HashSet<>(cells.values());
    for (RenderSurface.Viewer viewer : surface.viewers()) {
      Set<BlockPos> visible =
          DisplayViewerSet.visibleTo(
              occupied,
              surface::worldSolid,
              viewer.eyeX(),
              viewer.eyeY(),
              viewer.eyeZ(),
              candidates);
      for (Map.Entry<BlockDisplay, BlockPos> entry : cells.entrySet()) {
        if (visible.contains(entry.getValue())) {
          surface.showTo(viewer.id(), entry.getKey());
        } else {
          surface.hideFrom(viewer.id(), entry.getKey());
        }
      }
    }
  }

  private Map<BlockDisplay, BlockPos> displayWorldCells(Vehicle ship) {
    Map<BlockDisplay, BlockPos> cells = new IdentityHashMap<>();
    for (Map.Entry<BlockDisplay, ShipBlock> entry : pairDisplays(ship).entrySet()) {
      cells.put(entry.getKey(), ShipTransform.cell(ship, entry.getValue().pos()));
    }
    Map<String, BlockDisplay> sails = pairSails(ship);
    List<SailPiece> pieces = plates(ship);
    for (int i = 0; i < pieces.size(); i++) {
      BlockDisplay display = sails.get(Integer.toString(i));
      if (display == null) {
        continue;
      }
      SailPiece piece = pieces.get(i);
      cells.put(
          display,
          new BlockPos(
              (int) Math.floor(ship.origin().x() + ship.pose().x() + piece.originX()),
              (int) Math.floor(ship.origin().y() + ship.pose().y() + piece.originY()),
              (int) Math.floor(ship.origin().z() + ship.pose().z() + piece.originZ())));
    }
    return cells;
  }

  private void spawnCannonControls(Vehicle ship) {
    for (dev.mintychochip.archimedes.cannon.CannonMount mount :
        dev.mintychochip.archimedes.cannon.ShipCannons.discover(ship)) {
      surface.spawnInteraction(
          controlLocation(ship, mount.control()),
          interaction -> {
            interaction.setPersistent(false);
            interaction.setInteractionWidth(0.75F);
            interaction.setInteractionHeight(1.0F);
            interaction.setResponsive(true);
            interaction
                .getPersistentDataContainer()
                .set(shipKey, PersistentDataType.STRING, ship.id().toString());
            interaction
                .getPersistentDataContainer()
                .set(cannonControlKey, PersistentDataType.STRING, key(mount.control()));
          });
    }
  }

  private Location controlLocation(
      Vehicle ship, dev.mintychochip.archimedes.model.BlockPos control) {
    ShipTransform.VisualPosition position = ShipTransform.visual(ship, control);
    return surface.location(
        ship.origin(),
        position.x() - ship.origin().x() + 0.5,
        position.y() - ship.origin().y(),
        position.z() - ship.origin().z() + 0.5);
  }

  private List<SailPiece> plates(Vehicle ship) {
    return SailMesh.tessellate(SailMesh.cellsOf(ship.intactBlocks()), wind);
  }

  private BlockDisplay spawnSail(Vehicle ship, SailPiece piece, int index) {
    BlockData data = surface.blockData(SailMesh.worldData(piece.appearance()));
    String sailIndex = Integer.toString(index);
    return surface.spawnBlockDisplay(
        SailTransform.location(surface, ship, piece),
        d -> {
          d.setBlock(data);
          d.setPersistent(false);
          d.setTeleportDuration(ShipRenderer.TELEPORT_DURATION_TICKS);
          d.setVisibleByDefault(false);
          d.getPersistentDataContainer()
              .set(shipKey, PersistentDataType.STRING, ship.id().toString());
          d.getPersistentDataContainer().set(sailKey, PersistentDataType.STRING, sailIndex);
          d.setTransformation(SailTransform.transformation(piece));
        });
  }

  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  private void cleanupRender(Vehicle ship, ShipRuntimeException failure) {
    try {
      surface.removeTagged(shipKey, ship.id().toString());
    } catch (RuntimeException cleanup) {
      failure.addSuppressed(cleanup);
    }
    throw failure;
  }

  /**
   * Removes every tagged display for the ship. This is idempotent when no matching display exists.
   *
   * @param ship the ship to clean up
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public void removeRuntime(Vehicle ship) {
    try {
      surface.removeTagged(shipKey, ship.id().toString());
      ragdolls.entrySet().removeIf(entry -> entry.getValue().shipId().equals(ship.id()));
    } catch (RuntimeException failure) {
      if (failure instanceof ShipRuntimeException) {
        throw (ShipRuntimeException) failure;
      }
      throw new ShipRuntimeException("Renderer removal failed for ship " + ship.id(), failure);
    }
  }

  /**
   * Removes every tagged display owned by this renderer's ship key, including stale displays from
   * ships no longer present in memory. Failures are normalized to {@link ShipRuntimeException}.
   */
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
    ragdolls.clear();
  }

  /**
   * Teleports every tagged display to positions derived from the ship model. The {@code oldY}
   * argument describes the pose before the caller updated the model; {@code newY} describes the
   * resulting pose and is retained for the renderer contract even though the current model-derived
   * locations provide the authoritative destination.
   *
   * @param ship the ship whose displays are moved
   * @param oldY previous pose y
   * @param newY resulting pose y
   */
  @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
  public void reposition(Vehicle ship, double oldY, double newY) {
    try {
      Map<BlockDisplay, ShipBlock> blocksByDisplay = pairDisplays(ship);
      for (Map.Entry<BlockDisplay, ShipBlock> entry : blocksByDisplay.entrySet()) {
        surface.teleport(entry.getKey(), location(ship, entry.getValue()));
      }
      Map<String, BlockDisplay> sails = pairSails(ship);
      List<SailPiece> pieces = plates(ship);
      for (int i = 0; i < pieces.size(); i++) {
        BlockDisplay display = sails.get(Integer.toString(i));
        if (display != null) {
          SailPiece piece = pieces.get(i);
          surface.teleport(display, SailTransform.location(surface, ship, piece));
          display.setTransformation(SailTransform.transformation(piece));
        }
      }
      Map<String, org.bukkit.entity.Interaction> controls = new HashMap<>();
      for (org.bukkit.entity.Interaction interaction :
          surface.taggedInteractions(shipKey, ship.id().toString())) {
        String control =
            interaction
                .getPersistentDataContainer()
                .get(cannonControlKey, PersistentDataType.STRING);
        if (control != null) {
          controls.put(control, interaction);
        }
      }
      for (dev.mintychochip.archimedes.cannon.CannonMount mount :
          dev.mintychochip.archimedes.cannon.ShipCannons.discover(ship)) {
        org.bukkit.entity.Interaction interaction = controls.get(key(mount.control()));
        if (interaction != null) {
          surface.teleport(interaction, controlLocation(ship, mount.control()));
        }
      }
      cullViewers(ship);
    } catch (ShipRuntimeException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new ShipRuntimeException("Renderer reposition failed for ship " + ship.id(), failure);
    }
  }

  private org.bukkit.Location location(Vehicle ship, ShipBlock block) {
    ShipTransform.VisualPosition position = ShipTransform.visual(ship, block.pos());
    return surface.location(
        ship.origin(),
        position.x() - ship.origin().x(),
        position.y() - ship.origin().y(),
        position.z() - ship.origin().z());
  }

  private Map<BlockDisplay, ShipBlock> pairDisplays(Vehicle ship) {
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

  private Map<String, BlockDisplay> pairSails(Vehicle ship) {
    Map<String, BlockDisplay> sails = new HashMap<>();
    for (BlockDisplay display : surface.tagged(shipKey, ship.id().toString())) {
      String index = display.getPersistentDataContainer().get(sailKey, PersistentDataType.STRING);
      if (index != null) {
        sails.put(index, display);
      }
    }
    return sails;
  }

  private static String key(ShipBlock block) {
    return block.pos().x() + "," + block.pos().y() + "," + block.pos().z();
  }

  private static String key(dev.mintychochip.archimedes.model.BlockPos pos) {
    return pos.x() + "," + pos.y() + "," + pos.z();
  }

  @Override
  public void spawnClothRagdoll(
      Vehicle ship, UUID debrisId, String appearance, double x, double y, double z) {
    Location location =
        surface.location(
            ship.origin(), x - ship.origin().x(), y - ship.origin().y(), z - ship.origin().z());
    BlockDisplay display =
        surface.spawnBlockDisplay(
            location,
            d -> {
              d.setBlock(surface.blockData(SailMesh.worldData(appearance)));
              d.setPersistent(false);
              d.setTeleportDuration(ShipRenderer.TELEPORT_DURATION_TICKS);
              d.getPersistentDataContainer()
                  .set(shipKey, PersistentDataType.STRING, ship.id().toString());
            });
    ragdolls.put(debrisId, new Ragdoll(ship.id(), display));
    for (BlockDisplay sail : pairSails(ship).values()) {
      sail.remove();
    }
    List<SailPiece> pieces = plates(ship);
    for (int i = 0; i < pieces.size(); i++) {
      spawnSail(ship, pieces.get(i), i);
    }
  }

  @Override
  public void moveClothRagdoll(
      UUID debrisId, double x, double y, double z, double qx, double qy, double qz, double qw) {
    Ragdoll ragdoll = ragdolls.get(debrisId);
    if (ragdoll == null || ragdoll.display().isDead()) {
      return;
    }
    BlockDisplay display = ragdoll.display();
    Location location = display.getLocation();
    location.setX(x);
    location.setY(y);
    location.setZ(z);
    surface.teleport(display, location);
    display.setTransformation(
        new Transformation(
            new Vector3f(),
            new Quaternionf((float) qx, (float) qy, (float) qz, (float) qw),
            new Vector3f(1, 1, 1),
            new Quaternionf()));
  }
}
