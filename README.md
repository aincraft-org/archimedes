# Archimedes

[![Build](https://img.shields.io/github/actions/workflow/status/aincraft-org/archimedes/ci.yml?branch=master&label=build)](https://github.com/aincraft-org/archimedes/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/aincraft-org/archimedes)](LICENSE)
[![Release](https://img.shields.io/github/v/release/aincraft-org/archimedes)](https://github.com/aincraft-org/archimedes/releases/latest)
![Platform](https://img.shields.io/badge/Paper-26.2-blue)

A Paper 26.2 plugin that turns ordinary block builds into ships you can stand on. The original blocks leave the world; what remains is a moving picture of the hull plus a solid deck. Ships persist across restarts.

Player-facing guides live in [`content/docs/`](content/docs/). Living engineering specs live in [`docs/specs/`](docs/specs/).

## Requirements

- JDK 25
- Paper 26.2
- The Gradle wrapper in this repository (9.7.1)

## Build

```bash
./gradlew clean check
```

That is the quality gate: tests plus Spotless, Checkstyle (Google Checks 13.11.0), PMD, and SpotBugs. Local builds version as `YYYY.MM.DD-SNAPSHOT`. CI versions as `YYYY.MM.DD.<run>`. Stable GitHub release tags are that full CalVer value with no `v` prefix (for example `2026.08.21.9`). Pass `-PbuildVersion` (or the `releaseVersion` alias) to override; older `archimedes.version` / `ARCHIMEDES_VERSION` overrides are gone.

```bash
./gradlew runServer
```

Downloads Paper 26.2 and launches a test server with the plugin jar.

Nightly builds publish a rolling `nightly` pre-release. Stable releases use the full CalVer tag.

## License

MIT. See [LICENSE](LICENSE).
