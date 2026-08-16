# Ships Plugin — Specs Index

Maintained living specs for the Ships Paper plugin (`dev.jlo.ships`, Paper 26.2, Java 25). Update these files when design intent, invariants, or feature progress changes. Dated one-shot design docs and plans live under `docs/superpowers/` and are historical; these specs are the current authority.

## Domains

| Spec | Scope | Status |
|------|-------|--------|
| [ship-model](ship-model.md) | Ship data, transform projection, scanning, persistence, config | active |
| [ship-runtime](ship-runtime.md) | Display rendering, collision hulls, spawn/move/remove transactions, entity carry, reconciliation | active |
| [buoyancy](buoyancy.md) | Vertical rigid-body physics, waterline, bobbing, pose persistence | active |
| [commands](commands.md) | `/ship` surface, permissions, targeting | active |

## Cross-cutting facts (all domains)

- Pattern: domain interfaces in `dev.jlo.ships.{model,ship,render,collision,buoyancy,scan,store,config}`; Bukkit adapters live in `dev.jlo.ships.bukkit`. Domain code generally avoids Bukkit, but `ShipRuntimeImpl.removeAllTagged()` has a concrete Bukkit-adapter type check to expose stale-entity cleanup; this is a known boundary exception/debt, not a purity guarantee.
- Canonical transform is the canonical projection for rendering and hulls: visual = `origin + y + relative`; authoritative cell = `origin + floor(y) + relative`; collision anchor = visual `+ (0.5,0,0.5)`. World-boundary code may derive integer cells from `origin + floor(pose)` but must not duplicate visual/anchor arithmetic.
- Entities are non-persistent; `ships.json` is the single persistence authority; restart reconstructs deterministically.
- Mutations are transactional: validate → mutate → persist on the happy path; rollback is scoped to `ShipRuntimeException` (spawn/move) with suppressed cleanup failures; `remove`/`removeAll`/collision removal propagate directly. Unchecked adapter/entity failures may bypass cleanup.
- Current runtime is bound to the primary Bukkit world only. Command resolution may identify another world, but assembly there is rejected; cross-world runtime support remains Future. Once Task 3 lands, `disabled-worlds` must also reject the bound world.
- Ownership: ships are player-owned; disassembly requires owner or operator.
- Errors: Current command output adds operation prefixes, but some service and adapter failures already contain prefixes, so users can see duplicated wording. Reason-only service messages with command-owned prefixes remain a Next alignment target; this is unresolved rather than an implemented ownership decision. `ShipRuntimeException` represents runtime faults; plugin disables on enable/load/reconciliation failure.
- Quality gate: `./gradlew check` — Spotless, Checkstyle, PMD, SpotBugs all fail-on-violation. Tests mirror packages.

## Known debt & stale docs (tracked in per-domain specs)

- `docs/superpowers/specs/2026-08-13-ship-building-design.md` is stale (immutable ships, barrier decks, capability checks, inspect reports owner+origin).
- `ships.json` has no schema version; compatibility via optional fields only (see ship-model).

## How to update

1. Read the domain spec before designing/implementing in that domain.
2. Flip checkboxes in the file when work is verified; update `Last updated`.
3. New ideas land in Next/Future, not chat-only; promote Future → Next → Current deliberately.
4. Record irreversible choices in the Decisions log; leave unresolved forks as Open questions.
