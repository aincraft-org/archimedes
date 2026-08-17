# Sail Propulsion and Steering Ideas

> **Status:** Ideas only. Not an implementation contract. Not ship gameplay.
> **Related:** `docs/specs/physics.md`, `docs/superpowers/specs/2026-08-17-medium-propulsion-design.md`
> **Date:** 2026-08-17

Sails are another attachable catalog force, in the same style as `MediumThrustForce` and density-scaled drag. They are not a `Body` subclass, not a vehicle type, and not a `/ship sail` command. A later airship or wind-ship is still a force list.

This note is only the force picture: what a sail pushes on, how you steer that push, and what the catalog is missing.

## Why `MediumThrustForce` is not a sail

`MediumThrustForce` is

```text
F = k · ρ(p) · n̂
τ = (p − X) × F
```

That is a screw or a prop: a baked axis, scaled by density at a point. It is the wrong law for cloth in wind.

- It does not use relative wind. It pushes even when the air is still relative to the sail.
- It does not use area or sheet angle except as a lumped `k`. Changing heading does not change the force the way a sail does.
- It cannot go upwind. There is no lift perpendicular to the flow.
- It is not zero at rest in still air *because of flow*; it is zero only when `ρ = 0` or `k = 0`. A parked sail in a breeze should push. A parked sail in still air should not.

`ThrustForce` (`F = k n̂`) is worse: density-blind rocket.

`LiftForce` is closer (quadratic in speed, ~0 at rest) but still not a sail:

- It uses body velocity, not relative wind, so a ship sitting in a breeze produces no force.
- `ρ` is baked into the coefficient.
- Lift is locked to a body axis, not perpendicular to the incoming flow.
- Air is not `FluidField.isFluid`. Atmosphere stays a `DensityField`.

A sail force has to mention **air density**, **area and orientation**, and **relative wind**. Medium thrust has only the first.

## Shared vocabulary

```text
p       = X + R(localPoint)                 // sail center of pressure
n̂      = R(localNormal)                    // unit sail normal (pressure sail)
ĉ      = R(localChord)                     // unit chord, bow-ward along the boom (lifting sail)
A       = sail area                         // m², baked into the force
ρ       = medium.density(p)                 // DensityField, usually uniform air
v_wind  = wind.velocity(p)                  // missing from World today
v_app   = v_wind − v − ω × (p − X)          // apparent / relative wind at the sail
q       = ½ ρ |v_app|²
```

`v_app = 0` ⇒ no sail force. Vacuum (`ρ = 0`) ⇒ no sail force. `isFluid` is never consulted.

Wind is a flow field, not a density field. Reusing `DensityField` for “is there air” is correct. Reusing it for “which way is the wind” is not.

---

## Propulsion ideas

Each idea is one `Force` the caller attaches. Same body can carry several. Rebuild the body (already the ship-client pattern) when sheet angle or area changes; forces stay immutable.

### 1. Pressure sail (flat plate / square / downwind)

The simplest honest sail. Relative wind punches the cloth; only the normal component counts.

```text
cosθ = n̂ · v̂_app                            // 0 if |v_app| = 0
F    = q A (n̂ · v̂_app) |n̂ · v̂_app| n̂     // or q A max(n̂ · v̂_app, 0)² n̂
τ    = (p − X) × F
```

The `max(..., 0)` form is one-sided cloth (no push from the back). The signed-square form is a thin plate that works from either side.

**What it does:** strong downwind, dead in irons, weak or useless close-hauled. A raft with a blanket. Force grows with `ρ`, `A`, and `|v_app|²`, and the direction follows the sail normal, not a baked thrust axis.

**Catalog shape:** `PressureSailForce(localPoint, localNormal, area, medium, wind)`.

### 2. Lifting sail (Bermuda / lateen / airfoil)

A sail that can go upwind. Split the force into lift (perpendicular to `v_app`) and drag (along `−v_app`).

```text
α    = angle from v_app to the sail chord ĉ   // sheet sets ĉ relative to the hull
C_L  = C_L(α)                                 // odd in α; 0 at α = 0; stall later
C_D  = C_D0 + k C_L²                          // always resists the apparent wind
L    = q A C_L
D    = q A C_D
l̂    = n̂_flow × (v̂_app × n̂_flow)            // in the sail plane, ⊥ v_app
F    = L l̂ − D v̂_app
τ    = (p − X) × F
```

A first catalog cut can use a linear `C_L = c α` with a clamp, and a constant `C_D`. Stall tables stay out of scope.

**What it does:** close-hauled the lift vector has a forward component along the hull; the keel / hull side-force (a later sibling, not this force) cancels leeway. At rest in a breeze it still produces force, unlike today’s `LiftForce`.

**Catalog shape:** `LiftingSailForce(localPoint, localChord, localSpan, area, medium, wind)`.

### 3. Drag bag (spinnaker / square set for running)

Same law as the pressure sail, larger `A`, normal held near `v_app`. This is not a third law. It is the pressure sail with a different area and a different sheet. Worth naming because the *control* is “let it fill and present area,” not “set an angle of attack.”

**Catalog shape:** same `PressureSailForce`, larger `A`, `localNormal` aimed downwind.

### 4. High kite (same lifting law, high application point)

Same `LiftingSailForce` law. `localPoint` is several body-lengths up. The interesting part is torque: a tall `r` makes a large heel and a usable yaw couple. Propulsion is unchanged. This is how you get “kite instead of mast” without a new force type.

---

## Steering with sails

Steering here means changing the force and torque the sail list puts on the body. Rudders and hull skegs are a different force and are not required for these ideas.

### A. Sheet / sail angle

Change the sail’s orientation relative to the hull: rotate `localNormal` or `localChord` about the mast.

- Pressure sail: sheet changes which way `n̂` points, so the push direction and the `n̂ · v_app` factor both change. Sheeted flat to the wind (`n̂ ∥ v_app`) is maximum downwind push. Sheeted edge-on (`n̂ ⊥ v_app`) is ~0.
- Lifting sail: sheet sets angle of attack `α`. That is the primary throttle *and* the pointing control. Ease the sheet → lower `α` → less lift, less heel. Over-sheet → stall (when that curve exists).

Because catalog forces are immutable, sheeting is “construct a new force with a new local orientation and rebuild the body,” the same way medium-thrust throttle is a new coefficient.

This is not medium thrust with a different `n̂`. Medium thrust ignores `v_app`; a sheeted sail’s force *depends* on `v_app` and can reverse or vanish when the apparent wind changes.

### B. Offset center of pressure (yaw from `r × F`)

Put the sail off the centerline, or accept that the center of pressure is not on the mast axis.

```text
τ = (p − X) × F
```

A force that is not through `X` yaws (and heels). A staysail tacked to port produces a starboard-bow moment; a main whose CoP sits aft of the center of lateral resistance produces weather helm.

This reuses the application-point torque already specified for `MediumThrustForce`. The sail force supplies `F`; the point supplies `τ`. No extra couple.

### C. More than one sail

Attach two or more sail forces to the same body.

| Rig | What the list is | How you steer |
|---|---|---|
| Jib + main | Two lifting sails, different `p` and different sheets | Sheet the jib vs the main: more jib → lee helm, more main → weather helm |
| Fore + mizzen | Two pressure or lifting sails far apart on `z` | Opposite sheets make a pure yaw couple with little net drive |
| Port / starboard square | Two pressure sails, normals not parallel | Ease one, fill the other: yaw without a rudder |
| Main + kite | Lifting sail at deck + lifting sail high | High kite dominates heel; sheet it independently of the main |

Multiple sails are composition, not a new type. Each remains `PressureSailForce` or `LiftingSailForce`. The body does not become a `SailingShip`.

A useful identity: two equal-and-opposite sail forces at different points are a couple. That is sail-only steering even when net `F ≈ 0`.

---

## What the catalog already has vs what it does not

### Reuse

- **`DensityField`** for `ρ` at the sail point. Air is `DensityField.uniform(ρ_air)` or a later atmosphere field. Never `FluidField.isFluid`.
- **Application point + `τ = r × F`**, same pattern as `MediumThrustForce`. Offset sails steer for free once `F` is right.
- **Immutable `Force` + rebuild the body** for sheet/area changes. No mutable throttle on the force.
- **Force lists, not subclasses.** A wind-ship is gravity + hull drag + one or more sail forces. An airship later can attach the same sail types in thin air.
- **`QuadraticDragForce(c, DensityField)`** as hull/airframe drag beside the sails, so there is a terminal speed against the wind.

### Missing (do not fake these with medium thrust)

- **Wiring sails to `FlowField`.** The type now exists (`still`, `uniform`, `box`, `compose`) and is constructor-injected. Sails are not attached yet. `v_app = v_wind − v − ω × r` still needs a sail force to sample it. Still air plus body motion is not enough: a ship at rest in a breeze must move.
- **Apparent wind at an offset point** (`ω × r`). Optional for a first cut if `ω` is small; required for honest kite / heel coupling.
- **Angle-of-attack coefficients** for the lifting sail. Linear `C_L = c α` is enough to start. Stall, reefing, and points-of-sail tables stay out.
- **A side-force / keel sibling** if we want upwind track without sliding. That is not a sail. It is a hull force (`F` opposing leeway, possibly another `DensityField.liquid` unit). Do not hide it inside the sail.

### Explicit non-proposals

- No `SailingBody` / `Sailboat` subclass.
- No `/ship sail` or player sheet UI in this slice.
- No `World` change required to *write the force* if `FlowField` is constructor-injected. Adding `World.flowField()` later is optional convenience, same debate as `World.densityField()` (rejected for medium thrust so two vehicles can share a world and differ by attached fields).
- Do not set `isFluid` true for air so sails “see atmosphere.”

---

## Recommended first cut (when someone implements)

One new force, not four:

**`PressureSailForce`** — idea 1. It already needs `DensityField`, area, orientation, relative wind, and `r × F`. That is enough to prove “resting body in a breeze starts to move” and “sheet edge-on kills the force” and “offset sail yaws.”

`LiftingSailForce` is the next catalog unit, once a `FlowField` exists and the pressure sail’s tests are green. Kites and spinnakers are parameterizations of those two.

Until `FlowField` exists, do not ship a sail that secretly calls `MediumThrustForce`. The laws are not compatible.
