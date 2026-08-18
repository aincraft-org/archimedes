# Commands & Permissions — Living Spec

> Status: active
> Last updated: 2026-08-17
> Owners: jlo

## Intent

Expose ship operations to players through `/arch` (`/ship` remains an alias). The command layer is thin: parse, target, authorize, delegate to services, report errors. All state transitions live in services; commands never own them.

Success looks like: every subcommand has a permission, explicit error messages for every failure mode, tab completion that matches reality, and player-only enforcement. Known gaps are tracked under Next (test coverage, sink arity, tab completion).

## Boundaries

### In scope

- `ShipCommand`, `ShipTabCompleter`
- `TargetResolver` / `BukkitTargetResolver` (line-of-sight targeting, config distance) — assemble only
- `ShipTargeting` — inspect / disassemble / kill pick the hull whose volume (deck + 1.5 standing margin) is nearest the player, within `target-distance`
- `plugin.yml` command + permission declarations
- User-facing outcome messages (success/error per operation)

### Out of scope / non-goals

- Command implementation of ship logic (assembly, disassembly, buoyancy, kill — delegate to `ShipService`)
- Permissions beyond the eight declared nodes (`archimedes.command` parent + seven subnodes)
- Console execution (player-only by design)

## Commands

| Command | Permission | Behavior |
|---------|-----------|----------|
| `/arch assemble` | `archimedes.assemble` | Target block → `service.assembleAt` |
| `/arch inspect` | `archimedes.inspect` | `ShipTargeting.nearest` (standing-on / nearby hull, any owner) then `ShipPhysics.inspect`: pose, vel, mass, riders, cloth, submerged, chunk loaded, last-tick/sample ms, each force/torque, net force. Sail cells that share a facing collapse to one vector (`Sail +Z 25m2`) with summed area and force. Vector components are color-coded (X red, Y green, Z aqua). |
| `/arch disassemble` | `archimedes.disassemble` | Nearby hull via `ShipTargeting`; owner or operator only; restores world blocks then removes runtime |
| `/arch kill [all]` | `archimedes.kill` | Nearby hull: destroy runtime + persistence without restoring blocks (owner or operator). `/arch kill all` wipes every loaded ship and requires operator. |
| `/arch buoyancy` | `archimedes.buoyancy` | Toggle for the requester's owned ship in the current world (`toggleBuoyancy(requester, world)` — not line-of-sight-targeted) |
| `/arch sink <n>` | `archimedes.sink` | Positive integer parse; extra args silently ignored (no arity validation); delegates to service |
| `/arch sail [small\|medium\|large]` | `archimedes.sail` | Spawns a predetermined sail 3 blocks in front of the player via `service.spawnSail`. Default is `medium` (5×5 deck / 5×5 wool). Each wool block is 1 m² of pressure sail, so larger sizes produce more drive. No scan, no world-block clear. A dry or blocked `rise` is ignored so land spawns stay in the world. |

- Assembly delegates only after service world policy: non-bound targets fail first with `Ship assembly is not permitted in this world`; the configured primary world then fails with `Ship assembly is disabled in this world` when disabled. Both failures occur before scanner or world mutation.
- Player-facing assembly errors retain the service reason after the command's `Cannot assemble: ` prefix.

## Implementation guidance

- `TargetResolver` lives in `:api` (Bukkit `Player` leak via `compileOnly` `paper-api`); `ShipTargeting` is Paper-free in `:api` and scores hull AABBs (visual corners, +1.5 standing margin on +Y). `ShipCommand`, `ShipTabCompleter`, and `BukkitTargetResolver` live in `:paper`. Tests inject fakes (no live player).
- Assembled ships have no world blocks, so inspect / disassemble / kill cannot use block line-of-sight. They pick the nearest hull AABB in the player's world within `target-distance`. A player standing on a deck has distance 0.
- Tab completion: first argument is the subcommand list; `/arch sail` also completes `small|medium|large`; `/arch kill` completes `all`. No permission filtering. Other later arguments still return `List.of()`.
- Messages: user-facing and terse. Service failures are reason-only and command-owned prefixes render (`Cannot assemble: <lastError()>`, `Cannot disassemble: <lastError()>`, `Cannot kill: <lastError()>`, `Cannot toggle buoyancy: <lastError()>`, `Cannot lower ship: <lastError()>`). Missing spatial target is `No ship nearby.`

## Current

- [x] Seven subcommands routed with seven per-subcommand checks, plus the Bukkit-enforced parent `archimedes.command` (`plugin.yml` `permission:` field) — eight effective nodes, all `default: true`
- [x] `/arch` is the command (`/ship` alias); `/arch sail [small|medium|large]` spawns a named-size demo sail (default medium)
- [x] `/arch inspect` reports pose, velocity, mass factors, chunk/submerged state, tick/sample timing, and each sampled force
- [x] Inspect sail lines are one vector per facing (summed area/force); force vectors are RGB-colored (X/Y/Z)
- [x] Inspect / disassemble / kill target the nearby hull (standing-on or nearest AABB), not `findOwnedInWorld`'s first owned ship
- [x] `/arch kill` destroys a nearby ship without restoring blocks; `/arch kill all` wipes every loaded ship (operator)
- [x] Player-only enforcement: single entry check gates all subcommands with one generic message (`Only players can build ships.`)
- [x] Line-of-sight targeting capped at `target-distance` — assemble only
- [x] Tab completion of subcommands
- [x] Command tests with fakes (assemble permission rejection, delegation, disassemble failure messages; see gaps in guidance)

### Current notes

- `/arch inspect` is a multi-line physics snapshot; `/ship` still works as an alias.
- Leftover persisted hulls made `findOwnedInWorld` (first match) inspect the wrong ship. Spatial targeting replaces that for inspect / disassemble / kill.

## Next

 - [x] Inspect output decision: retain `Ship <8-char id> | blocks=<count>`; no owner/origin fields are required (2026-08-16).
 - [ ] Consider op-only default for `archimedes.command` if shared-server deployment needs it (currently `default: true`)
 - [x] Add argument completion for `sail` sizes (`small`, `medium`, `large`)
 - [ ] Add argument completion for `sink` (positive integer)

## Future

- [ ] `/ship list`, `/ship info <id>` for operator debugging
- [ ] Permission per-player defaults flipped to op-only if used on shared servers

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | Living specs in `docs/specs/`; dated docs stay in `docs/superpowers/` | User directive |
| 2026-08-16 | Services return reason-only failures; commands own exactly one operation prefix | Prevent duplicated user-facing error text and keep service reasons reusable |
| 2026-08-16 | Runtime is bound to the primary Bukkit world; cross-world support remains Future | Current assembly/runtime wiring uses the primary world |
| 2026-08-14 | `/ship collision-test` debug fixture added behind op permission; kept isolated from production persistence | Spike acceptance; fixture since removed from code (verified 2026-08-16) |
| 2026-08-17 | `/ship sail` spawns a predetermined template via `spawnSail` | User asked for one command that drops a fixed-size sail without building |
| 2026-08-17 | Command is `/arch` with `/ship` alias; inspect samples live forces | User asked for arch prefix and force/performance metrics |
| 2026-08-17 | `/arch sail` accepts `small`/`medium`/`large`; default is medium | User asked for bigger hulls and named size variants |
| 2026-08-17 | Inspect merges same-facing sails and color-codes vector XYZ | User asked for one sail vector per direction and readable force colors |
| 2026-08-17 | Inspect / disassemble / kill use nearest hull AABB, not first owned ship | `findOwnedInWorld` returned leftover distant hulls; assembled ships have no LOS blocks |
| 2026-08-17 | `/arch kill` destroys without restore; `/arch kill all` is operator-only | User asked for a wipe that does not put blocks back |

## Open questions

- [x] `/ship collision-test` was removed from both `src/main` and `src/test` (verified via grep) — legacy debug fixture fully gone; references exist only in dated docs. Removed from Future/Next lists above — no action needed. (Resolved 2026-08-16)
