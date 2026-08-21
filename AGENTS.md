# Archimedes

Paper 26.2 plugin (`api` / `common` / `paper`). Living domain specs are in `docs/specs/`.

## Project skills

Vendored from sibling `server-development-skills` into `.grok/skills/` (Grok loads that path; `.agents/skills` is a symlink to it). Follow a skill when the task matches its trigger:

| Skill | Use when |
|---|---|
| [project-setup](.grok/skills/project-setup/SKILL.md) | Gradle, wrapper, plugin.yml, toolchain pins, Paper 26.2 coordinates |
| [ci-release](.grok/skills/ci-release/SKILL.md) | GitHub Actions, CalVer, nightly/stable releases, README badges |
| [autonomous-testing](.grok/skills/autonomous-testing/SKILL.md) | Azalea bots, in-game task automation, packet completion checks |
| [database-integration](.grok/skills/database-integration/SKILL.md) | HikariCP, SQLite/MySQL, async persistence, migrations |
| [performance-optimization](.grok/skills/performance-optimization/SKILL.md) | Spark, TPS/MSPT, main-thread work, listener hygiene |
| [docs-maintenance](.grok/skills/docs-maintenance/SKILL.md) | End-user docs, Fumadocs, content/docs, docs drift |

Refresh from the source repo with:

```bash
rsync -a --delete --exclude '.git/' --exclude 'docs/' \
  ../server-development-skills/ .grok/skills/
```
