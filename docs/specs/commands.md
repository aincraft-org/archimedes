# Commands & Permissions — Living Spec

> Status: active
> Last updated: 2026-08-16
> Owners: jlo

## Intent

Expose ship operations to players through `/ship`. The command layer is thin: parse, target, authorize, delegate to services, report errors. All state transitions live in services; commands never own them.

Success looks like: every subcommand has a permission, explicit error messages for every failure mode, tab completion that matches reality, and player-only enforcement. Known gaps are tracked under Next (test coverage, sink arity, tab completion).

## Boundaries

### In scope

- `ShipCommand`, `ShipTabCompleter`
- `TargetResolver` / `BukkitTargetResolver` (line-of-sight targeting, config distance) — assemble only; other operations target the requester's owned ship in the current world
- `plugin.yml` command + permission declarations
- User-facing outcome messages (success/error per operation)

### Out of scope / non-goals

- Command implementation of ship logic (assembly, disassembly, buoyancy — delegate to `ShipService`)
- Permissions beyond the six declared nodes (`ships.command` parent + five subnodes)
- Console execution (player-only by design)

## Commands

| Command | Permission | Behavior |
|---------|-----------|----------|
| `/ship assemble` | `ships.assemble` | Target block → `service.assembleAt` |
| `/ship inspect` | `ships.inspect` | `findOwnedInWorld`; reports ship ID prefix + block count |
| `/ship disassemble` | `ships.disassemble` | Owner or operator only |
| `/ship buoyancy` | `ships.buoyancy` | Toggle for the requester's owned ship in the current world (`toggleBuoyancy(requester, world)` — not line-of-sight-targeted) |
| `/ship sink <n>` | `ships.sink` | Positive integer parse; extra args silently ignored (no arity validation); delegates to service |

## Invariants

- Player-only: one entry check (`instanceof Player`) gates all subcommands; console executors get a clear rejection message.
- Errors: no target, oversized/forbidden component, occupied restoration space, missing ownership, invalid sink argument — each explicit. Current command output adds operation prefixes, but some service/adapter failures already contain prefixes, causing duplicated wording. Reason-only service messages with command-owned prefixes remain an unresolved Next alignment target.
- `ships.command` is enforced by Bukkit via `plugin.yml` `permission:` field before `ShipCommand` runs; each subcommand additionally checks its own permission in the executor (`ships.assemble`, `ships.inspect`, `ships.disassemble`, `ships.buoyancy`, `ships.sink`).
- Disassembly authorization: owner or operator — enforced in the service (`requesterId`/`isOperator` params); the command looks up the requester's owned ship and passes `player.isOp()`.

## Implementation guidance

- `ShipCommand` keeps a `TargetResolver` (interface) + `ShipService`; tests inject fakes (no Bukkit).
- Tab completion: first argument only, subcommand list; **no permission filtering, no argument completion** (intentionally returns `List.of()` for later args) — keep honest about this limitation.
- Messages: user-facing and terse. Current failures can duplicate operation wording when a service/adapter message already contains a prefix. The Next target is reason-only service failures rendered with command-owned prefixes (`Cannot assemble: <lastError()>`, `Cannot disassemble: <lastError()>`, `Cannot toggle buoyancy: <lastError()>`, `Cannot lower ship: <lastError()>`).

## Current

- [x] Five subcommands routed with five per-subcommand checks, plus the Bukkit-enforced parent `ships.command` (`plugin.yml` `permission:` field) — six effective nodes, all `default: true`
- [x] Player-only enforcement: single entry check gates all subcommands with one generic message (`Only players can build ships.`)
- [x] Line-of-sight targeting capped at `target-distance` — assemble only
- [x] Tab completion of subcommands
- [x] Command tests with fakes (assemble permission rejection, delegation, disassemble failure messages; see gaps in guidance)

### Current notes

- `/ship inspect` reports `Ship <8-char id> | blocks=<count>` only — the dated design doc claimed owner and origin; implementation is minimal (no owner/origin output).

## Next

- [ ] Clarify inspect scope (ID+blocks vs doc's owner+origin) and update doc/impl to agree
- [ ] Consider op-only default for `ships.command` if shared-server deployment needs it (currently `default: true`)
- [ ] Add argument completion for `sink` (positive integer)
- [ ] Add permission-rejection tests for inspect/disassemble/buoyancy/sink; sink `< 1` boundary; `BukkitTargetResolver` and `ShipTabCompleter` tests

## Future

- [ ] `/ship list`, `/ship info <id>` for operator debugging
- [ ] Permission per-player defaults flipped to op-only if used on shared servers

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-16 | Living specs in `docs/specs/`; dated docs stay in `docs/superpowers/` | User directive |
| 2026-08-16 | Error ownership unresolved: current command prefixes can duplicate service/adapter prefixes; reason-only service messages and command-owned prefixes remain a Next target | Document current behavior without claiming implementation |
| 2026-08-16 | Runtime is bound to the primary Bukkit world; cross-world support remains Future | Current assembly/runtime wiring uses the primary world |
| 2026-08-14 | `/ship collision-test` debug fixture added behind op permission; kept isolated from production persistence | Spike acceptance; fixture since removed from code (verified 2026-08-16) |

## Open questions

- [x] `/ship collision-test` was removed from both `src/main` and `src/test` (verified via grep) — legacy debug fixture fully gone; references exist only in dated docs. Removed from Future/Next lists above — no action needed. (Resolved 2026-08-16)
