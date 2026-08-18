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
- `TargetResolver` / `BukkitTargetResolver` (line-of-sight targeting, config distance) — assemble only; other operations target the requester's owned ship in the current world
- `plugin.yml` command + permission declarations
- User-facing outcome messages (success/error per operation)

### Out of scope / non-goals

- Command implementation of ship logic (assembly, disassembly, buoyancy — delegate to `ShipService`)
- Permissions beyond the seven declared nodes (`archimedes.command` parent + six subnodes)
- Console execution (player-only by design)

## Commands

| Command | Permission | Behavior |
|---------|-----------|----------|
| `/arch assemble` | `archimedes.assemble` | Target block → `service.assembleAt` |
| `/arch inspect` | `archimedes.inspect` | `findOwnedInWorld` then `ShipPhysics.inspect`: pose, vel, mass, riders, cloth, submerged, chunk loaded, last-tick/sample ms, each force/torque, net force |
| `/arch disassemble` | `archimedes.disassemble` | Owner or operator only |
| `/arch buoyancy` | `archimedes.buoyancy` | Toggle for the requester's owned ship in the current world (`toggleBuoyancy(requester, world)` — not line-of-sight-targeted) |
| `/arch sink <n>` | `archimedes.sink` | Positive integer parse; extra args silently ignored (no arity validation); delegates to service |
| `/arch sail` | `archimedes.sail` | Spawns a predetermined 3×3 deck / 4-high mast / 3×3 wool sail 3 blocks in front of the player via `service.spawnSail`. No scan, no world-block clear. A dry or blocked `rise` is ignored so land spawns stay in the world. |

- Assembly delegates only after service world policy: non-bound targets fail first with `Ship assembly is not permitted in this world`; the configured primary world then fails with `Ship assembly is disabled in this world` when disabled. Both failures occur before scanner or world mutation.
- Player-facing assembly errors retain the service reason after the command's `Cannot assemble: ` prefix.

## Implementation guidance

- `TargetResolver` lives in `:api` (Bukkit `Player` leak via `compileOnly` `paper-api`); `ShipCommand`, `ShipTabCompleter`, and `BukkitTargetResolver` live in `:paper`. Tests inject fakes (no live player).
- Tab completion: first argument only, subcommand list; **no permission filtering, no argument completion** (intentionally returns `List.of()` for later args) — keep honest about this limitation.
 - Messages: user-facing and terse. Service failures are reason-only and command-owned prefixes render (`Cannot assemble: <lastError()>`, `Cannot disassemble: <lastError()>`, `Cannot toggle buoyancy: <lastError()>`, `Cannot lower ship: <lastError()>`).

## Current

- [x] Six subcommands routed with six per-subcommand checks, plus the Bukkit-enforced parent `archimedes.command` (`plugin.yml` `permission:` field) — seven effective nodes, all `default: true`
- [x] `/arch` is the command (`/ship` alias); `/arch sail` spawns a fixed-size demo sail
- [x] `/arch inspect` reports pose, velocity, mass factors, chunk/submerged state, tick/sample timing, and each sampled force
- [x] Player-only enforcement: single entry check gates all subcommands with one generic message (`Only players can build ships.`)
- [x] Line-of-sight targeting capped at `target-distance` — assemble only
- [x] Tab completion of subcommands
- [x] Command tests with fakes (assemble permission rejection, delegation, disassemble failure messages; see gaps in guidance)

### Current notes

- `/arch inspect` is a multi-line physics snapshot; `/ship` still works as an alias.

## Next

 - [x] Inspect output decision: retain `Ship <8-char id> | blocks=<count>`; no owner/origin fields are required (2026-08-16).
 - [ ] Consider op-only default for `archimedes.command` if shared-server deployment needs it (currently `default: true`)
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

## Open questions

- [x] `/ship collision-test` was removed from both `src/main` and `src/test` (verified via grep) — legacy debug fixture fully gone; references exist only in dated docs. Removed from Future/Next lists above — no action needed. (Resolved 2026-08-16)
