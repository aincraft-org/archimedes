# Curved Mesh Rendering in Minecraft — Review

> **Status:** Review of shipped code and the vanilla Paper path on 2026-08-17. Not an implementation contract.
> **Related:** `docs/specs/ship-runtime.md`
> **Proof:** `ShipRenderer` / `BukkitShipRenderer` / `RenderSurface`; `:paper` `ShipRendererTest`; `:api` `ShipTransformTest`

## Verdict

Platform limit: the server cannot upload an arbitrary triangle mesh to the GPU. A Paper plugin only spawns entities; the Java client draws vanilla or resource-pack **cuboid** models as block / item / text **display entities**. There is no “send this OBJ/glTF” packet.

Archimedes today is the first, simplest form of that: **one `BlockDisplay` per captured block**, placed at the visual corner, `setBlock` only. No `Transformation`, no `ItemDisplay`, no mesh.

A curved look is possible only as an **approximation**: many small cubes, or one item whose resource-pack model is many cubes, or a bone rig of item-displays. Hulls should stay voxel `BlockDisplay`s. Curved cloth, balloons, and props belong on a decorative overlay — not as a replacement for the assembled build.

## Platform limit

The plugin runs on the server. The client renders. Between them the protocol can spawn entities and set their NBT (block state, item stack, text, an affine `Transformation`). It cannot:

- stream vertex/index buffers
- register a new GPU mesh at runtime
- replace a block’s baked model without a **client** resource pack

Vanilla resource-pack models (`assets/.../models/*.json`) are also not freeform meshes. Wiki: model `elements` “can have only cubic forms.” Rotations are per-element (typically 22.5° steps). A “smooth hull” in vanilla is always a pile of boxes, whether you spawn them as many displays or bake them into one item model.

Client mods (Fabric/Forge baked models, OptiFine OBJ, Iris custom uniforms) are out of scope. This plugin is Paper, no client mod.

## Shipped visual path

| Step | Code | What happens |
|---|---|---|
| Project | `ShipTransform.visual(ship, relative)` | Exact block **corner**: `origin + pose + relative`. No `+0.5`. |
| Spawn | `ShipRenderer.render` and `BukkitShipRenderer.renderDisplays` | `surface.spawnBlockDisplay(location, d -> d.setBlock(data))` |
| Persist | `setPersistent(false)` + PDC `ship-id` / `ship-id-block` | Non-persistent; restart rebuilds from `archimedes.json` |
| Move | `BukkitShipRenderer.reposition` | `surface.teleport` to a newly computed visual corner. No display interpolation, no scale/rotate. |
| Collision | Shulker volumes at `collisionAnchor` | Separate from the picture. Not a mesh. |

`RenderSurface` only knows `BlockDisplay`. There is no `spawnItemDisplay`, no `setTransformation` / `setTransformationMatrix`, no mesh type.

That matches the product: assemble ordinary blocks, see those exact blocks float. A true curved mesh would stop being “your build.”

## Three ways to fake a curve

All three still draw cubes (or a text quad). They differ in **who owns the geometry** and **how many entities** you pay.

### 1. Tessellated `BlockDisplay` / `TextDisplay` pieces

Spawn many displays. Use Paper’s affine `Transformation` (translation, left rotation, scale, right rotation — see [Paper display entities](https://docs.papermc.io/paper/dev/display-entities/)) to squash a cube into a plate or wedge and tile a surface. `TextDisplay` is a one-sided quad (good for a sail billboard, bad for a solid hull). Client interpolation can animate the transform.

| Can | Cannot |
|---|---|
| Use the **same** captured `BlockData` (oak looks like oak) | Be a smooth mesh |
| Work with **no** resource pack | Stay cheap: a 2k-block ship is already 2k entities; tessellation multiplies that |
| Approximate a cylinder / sail with thin plates | Share one transform for the whole hull without parenting/passengers |

**Fit:** small decorative bits you generate at runtime from the build (a ring of wool plates). **Not** a replacement for the hull.

### 2. `ItemDisplay` plus a resource-pack model

One (or a few) `ItemDisplay`s. The item’s model comes from a resource pack (`custom_model_data` / 1.21.4+ `item_model`). The pack is a **server resource pack** the client must accept. Geometry is still cuboid elements, but they live in **one** entity, so a balloon or propeller is cheap.

| Can | Cannot |
|---|---|
| Look like a Blockbench “mesh” (many boxes) at one entity cost | Exist without a pack on every client |
| Swap variants (reefed sail, spinning blur) by changing the item | Be the player’s unique voxel hull — the model is authored, not scanned |
| Use item-display transform + interpolation | Stream a new mesh the player just built |

**Fit:** named cosmetics — envelope, lateen, screw, figurehead. Author once, attach to a mast block. **Not** “this wool wall becomes a NURBS sail.”

### 3. Server model engine on item-display packets

A library (or in-house bone walker) splits a Blockbench rig into bones. Each bone is an `ItemDisplay` whose pack model is that bone; the server writes `Transformation` every tick. Same protocol as (2), plus skeletal animation (billow, spin, heel).

| Can | Cannot |
|---|---|
| Animate sails / props without a client mod | Avoid a resource pack |
| Parent bones to the ship pose | Upload raw triangles; bones are still cuboid items |
| Stay a sibling overlay on the voxel hull | Scale to one bone per hull block without becoming (1) |

**Fit:** animated decorative parts. Wrong as the hull renderer. Do not pull this in until a specific part needs bones.

## What Archimedes should use

| Piece | Choice | Why |
|---|---|---|
| **Assembled hull** | Keep today’s `BlockDisplay` per captured block | The ship *is* the scan. Collision, buoyancy, and the picture all key off the same blocks. |
| **Static curve (balloon, carved bow)** | Option 2: one `ItemDisplay` + pack model, anchored to a marker block | One entity, authored curve, does not replace the hull. |
| **Cloth / one-sided sheet** | Option 1 `TextDisplay` or a thin transformed `BlockDisplay` if it must use the build’s wool; option 2 if it is a named sail cosmetic | Pressure-sail physics already maps wool/banners; the picture can stay voxel or become a pack sail. |
| **Spinning prop / billowing bone sail** | Option 3, later | Only if interpolation on a single item is not enough. |

Do **not** tessellate the whole hull. Do **not** wait for a GPU mesh API. Do **not** implement any of this until it is promoted out of Future.

## Non-goals (this review)

No renderer change, no resource pack, no model engine, no collision/physics/`ShipPose` change.
