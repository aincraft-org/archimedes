# Physics Models Review — Ships, Airships, and Sail Aesthetics

> **Status:** Review of shipped code on 2026-08-17. Not an implementation contract.
> **Related:** `docs/specs/physics.md`, `docs/superpowers/specs/2026-08-17-medium-propulsion-design.md`,
> `docs/superpowers/specs/2026-08-17-sail-propulsion-ideas.md`
> **Proof:** `VehiclePropulsionCompositionTest`, `ShipSailsTest`, `ShipPhysicsTest`, catalog class scan

## Verdict

The generic library **can** model both watercraft and airships. Vehicle type is a force list,
not a subclass. The same `PressureSailForce` drives a hull in liquid and an envelope in air.

That is **not** the same as “airships exist in the plugin.” Archimedes `ShipPhysics` always
attaches waterline lift (`ShipBuoyancyForce`). A dry build falls. There is no aerostatic
envelope, no propeller, no yaw, and no rider carry on XZ.

Sails are the only **medium-aware** horizontal actuator that is actually shipped. They are
the right law for cloth in wind. They will **not** look or feel like sailing — or like a
steampunk airship — until lifting sails and/or density-scaled props exist, and until the
ship client keeps orientation and carries riders sideways.

## What is shipped

Reusable catalog (`dev.mintychochip.phys`), attached by the caller:

| Force | Law | Medium | Role |
|---|---|---|---|
| `GravityForce` | `m g` | none | weight |
| `FluidBuoyancyForce` | `F = −ρ V g` | `DensityField` or world liquid | watercraft / aerostatic hover |
| `QuadraticDragForce(c)` | `−c \|v\| v` | lumped (ρ baked into `c`) | generic drag |
| `ThrustForce` | `k n̂` | **none** (rocket) | density-blind push |
| `LiftForce` | `c · \|v × n\|²` along body lift axis | ρ baked into `c`; **no wind** | airplane wing |
| `PressureSailForce` | `q A max(n̂ · v̂_app, 0)² n̂` | `DensityField` + `FlowField` | one-sided cloth |
| `SupportForce` / `CoulombFrictionForce` | contact plane | none | ground |
| `ViscousDragForce` / `AngularDragForce` | `−c v` / `−c ω` | none | linear / spin damping |

Supporting fields: `DensityField.uniform` / `liquid`, `FlowField.still` / `uniform` / `box` /
`compose`. `FluidField.isFluid` is ship water only. Air is never `isFluid`.

**Not on the classpath:** `MediumThrustForce` (approved, unbuilt), density-scaled
`QuadraticDragForce(c, DensityField)` (approved, unbuilt), `LiftingSailForce` (ideas only).

## Ships vs airships

### Library — yes, as compositions

```text
watercraft = Gravity + FluidBuoyancy(liquid) + [PressureSail | Thrust | later MediumThrust]
airship    = Gravity + FluidBuoyancy(uniform ρ_air) + [PressureSail | Thrust | later MediumThrust]
airplane   = Gravity + Lift(+ airspeed) + Thrust
```

`VehicleCompositionTest` already steps hover: liquid net-up, aerostatic net-up at rest,
wing lift ~0 at rest. `VehiclePropulsionCompositionTest` steps the **same sail** on both
a watercraft and an airship: both move downwind; the airship still hovers in a vacuum
`FluidField`; the watercraft still needs liquid.

`ThrustForce` also attaches to either list, but it does not care about density. A “prop”
built from it pushes just as hard in vacuum as in water. That is why the approved next
actuator is `MediumThrustForce` (`F = k ρ n̂`), not more rockets.

### Plugin — water ships only

`ShipPhysicsImpl` builds:

```text
Gravity + ShipBuoyancyForce [+ QuadraticDrag(0.05) + ShipSails when ticking]
```

`ShipBuoyancyForce` counts submerged colliders against `FluidField.isFluid`. Empty air
gives zero lift. The plugin wires `DensityField.uniform(1.2)` and `FlowField.uniform(+Z, 8)`
for **sails**, not for hover.

So:

- A wool-sailed raft **in water** can bob and slide south.
- The same raft **in the sky** falls and, if cloth faces the wind, also slides south.
- There is no in-game airship type, command, envelope gas, or ballast.

`ShipRuntime.move(oldY, newY)` is still a vertical transaction. Displays and hulls read
`ShipPose.x/z` after the pose is written, so XZ can appear to move. Rollback reconstructs
`new ShipPose(oldY)` and **wipes XZ**. Riders are carried on Y only. `ShipBody` is rebuilt
every tick at identity orientation; sail torque never becomes heading.

## Why sails make sense — and why they will not look right

The user’s instinct is correct.

### The law is honest for cloth

`PressureSailForce` is a square / downwind plate:

- Rest in a breeze starts moving (unlike `LiftForce` or a parked `ThrustForce` story).
- Still air is zero. Vacuum is zero. Edge-on sheet is zero. Wind on the back is zero.
- Force follows the cloth normal, not a baked engine axis.
- Same unit works on a water hull or an airborne envelope because `ρ` and wind are injected.

That is the right first sail. It is **not** a Bermuda / lateen / airfoil. There is no
angle-of-attack lift, so there is **no upwind component**. Every sheet either pushes
downwind along `n̂` or produces nothing. `VehiclePropulsionCompositionTest` asserts that
no facing of this force yields a force against the wind.

### What that looks like in game

1. **Downwind brick.** The ship does not point, tack, or sheet. It is blown. Square-rig
   running is the entire vocabulary.
2. **Wool has no facing.** `ShipSails` treats `*_wool`, `*_banner`, `*_wall_banner` as
   cloth. Missing `facing=` defaults to `+Z`. Wool is not directional in Minecraft, so a
   whole wool wall is a stack of independent 1 m² plates all facing south. Only banners
   and wall banners can aim another cardinal.
3. **No canvas.** Each block is its own sail. A “sail” is a pile of wool, not a sheet
   with a boom. There is no reef, no sheet angle, no player control.
4. **Global trade wind.** Plugin wind is a constant `+Z` at 8. Every sailed ship goes
   south forever. There is no weather helm, no lull, no heading into the eye.
5. **Torque is thrown away.** Offset sails compute `τ = r × F`, but `ShipPose` has no
   yaw and the body is reborn with `new Quaterniond()` each tick. The ship never turns
   to face the wind. Sliding sideways is the motion.
6. **Airship look.** A sailing airship is a valid fantasy (galleon envelope). With this
   law it is a falling/hovering crate skidding downwind. The usual airship picture —
   envelope + props / fans that die in vacuum — is `FluidBuoyancy(uniform air)` +
   `MediumThrustForce`, which is not shipped.

So: sails are the correct **catalog** choice for wind ships. They are a weak **aesthetic**
choice as the only airship drive, and a weak **gameplay** choice until lifting sails and
heading exist.

## Recommended split (not implemented here)

| Vehicle | Keep aloft | Drive | Feel |
|---|---|---|---|
| Watercraft | `FluidBuoyancy` / `ShipBuoyancy` (liquid) | `PressureSail` now; `LiftingSail` + keel later; water `MediumThrust` for screws | Sailing when lifting exists; motors when props exist |
| Airship | `FluidBuoyancy(uniform ρ_air)` | `MediumThrust` (props) as the default look; optional `PressureSail` as flavor | Hover at rest, push through air, props fade when `ρ → 0` |
| Airplane | `LiftForce` (needs airspeed) | `ThrustForce` (rocket) or later `MediumThrust` | Falls at rest, flies when moving |

Do **not** fake a sail with `MediumThrustForce` or a prop with `PressureSailForce`. The
laws answer different questions (`v_app` and area vs `k ρ` along an axis).

## Gaps that block the look, in order

1. `MediumThrustForce` + density drag — shared water screw / air prop (already designed).
2. Ship-client aerostatic option — otherwise “airships” are dry ships that fall.
3. `LiftingSailForce` — if sailing should look like sailing, not like being blown.
4. Yaw on `ShipPose` and retained orientation — otherwise nothing can point.
5. Runtime XZ carry and rollback that does not wipe `x/z`.

## What this review does not change

No new forces, commands, or gameplay wiring. Catalog and client stay as shipped. Findings
are persisted in `docs/specs/physics.md`.
