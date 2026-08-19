# Ship Cannons Design

## Intent

Let players build and use cannons on assembled ships without custom item models, a resource pack, ammunition inventory, or live world blocks. Cannons use captured vanilla blocks, remain attached to the ship while it moves, and fire through an interaction proxy on the rendered ship.

## Player experience

A cannon is a captured dispenser with a stone button on one of its six adjacent cells. The dispenser's saved `facing` property defines the muzzle direction. After assembly, right-clicking the rendered button fires that cannon. Only the ship owner or a server operator may fire it.

A successful click launches a visible vanilla cannonball from just beyond the dispenser face. The shot follows normal gravity, attributes damage to the firing player, and creates a modest non-incendiary impact explosion. It does not consume ammunition. Each cannon has a runtime cooldown; clicking during cooldown reports the remaining time.

## Discovery

A Paper-free cannon discovery component inspects the vehicle's captured `ShipBlock` values. It recognizes a dispenser only when:

- its block data contains a valid cardinal or vertical `facing` value;
- exactly one adjacent captured block is a stone button; and
- the button is attached to the dispenser according to its saved `face` and `facing` block-data properties.

Invalid or ambiguous structures are not registered. Discovery returns immutable cannon mounts containing the dispenser position, control position, and direction. No cannon data is added to `archimedes.json`; mounts are derived deterministically after load.

## Rendered interaction contract

Assembled blocks are `BlockDisplay` entities rather than live Bukkit blocks. Every rendered block display therefore carries persistent metadata for:

- owning ship UUID; and
- captured relative `BlockPos`.

The interaction listener accepts only tagged displays, resolves the vehicle through the live ship service, and checks whether the clicked relative position is a registered cannon control. Untagged, stale, malformed, non-button, or non-cannon displays are ignored. Existing renderer removal and stale-entity sweeps remove these displays normally.

## Components

- `CannonMount` and `CannonDirection`: Paper-free cannon identity and geometry.
- `ShipCannons`: deterministic discovery and lookup from captured blocks.
- `CannonService`: resolves control clicks, authorization, cooldown, transformed muzzle position, and launch delegation.
- `CannonLauncher`: boundary used by the service to launch a shot without importing Bukkit into the common module.
- `BukkitCannonLauncher`: spawns and configures the vanilla projectile.
- `BukkitCannonInteractionListener`: reads display metadata, resolves the player and ship, and delegates to `CannonService`.

The plugin owns one service and listener. Runtime cooldown state is keyed by ship UUID and cannon control position and is cleared when the ship is removed or the plugin disables.

## Coordinates and movement

Cannon positions are always derived from the current vehicle pose through `ShipTransform.visual`. The muzzle starts at the center of the dispenser display plus a direction offset sufficient to put the projectile outside the display and collision hull. Direction is the captured dispenser orientation; ship rotation is currently out of scope, so no rotational transform is needed.

## Projectile safety defaults

The Bukkit launcher uses a visible vanilla projectile suitable for a cannonball, gives it gravity and shooter attribution, disables incendiary behavior, and bounds explosion strength to a small constant. Projectile impact must not bypass Bukkit's normal event and protection-plugin hooks. Configuration is not added in this first slice; constants stay named and centralized so later configuration does not alter domain contracts.

## Error handling

Interaction failures do not mutate ship state. The player receives terse feedback for unauthorized use and cooldown. Missing ships, stale tags, malformed metadata, and controls that no longer resolve are ignored because they can arise during entity cleanup or restart reconciliation. Launcher failures are caught at the Bukkit boundary, logged with ship and cannon identity, and reported to the player without starting cooldown.

## Testing

Paper-free tests cover:

- valid horizontal and vertical dispenser/button structures;
- rejection of missing, detached, and ambiguous buttons;
- dispenser-facing direction;
- control-position lookup;
- owner authorization and operator override;
- per-cannon cooldown isolation;
- current-pose muzzle coordinates; and
- launch failure leaving the cannon ready.

Paper adapter tests cover display metadata round-trip and listener rejection of untagged or malformed displays. A runtime smoke test assembles or spawns a ship containing a cannon, right-clicks its rendered control, and observes one projectile launched from the moving cannon position.

## Scope

Included: discovery, rendered-button interaction, authorization, runtime cooldown, one safe projectile, user feedback, and tests.

Excluded: custom models, resource packs, ammunition, dispenser inventories, persisted cooldowns, ship damage, cannon recoil, aiming independent of dispenser facing, ship rotation, ballistic prediction, and configuration UI.
