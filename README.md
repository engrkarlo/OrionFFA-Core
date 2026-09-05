# OrionFFA-Core

Config-first FFA for Paper 26.2.x / Java 25. OrionFFA has one canonical command: `/orionffa` (alias `/ofa`).

## Build

Use Gradle 9.x with Java 25 (the target Paper API is compiled for Java 25):

```text
gradle build
```

The Paper API is `compileOnly`; the resulting plugin JAR contains no server API classes.

## First setup

1. Start the server once to create the configuration files.
2. Set the FFA lobby with `/orionffa setlobby`.
3. Save arenas with `/orionffa arena save <name>` and bind them with `/orionffa arena bind <name> <kit>`.
4. Configure kits and GUI presentation in `config.yml` and `guis.yml`, then run `/orionffa reload`.

Every configured location keeps its own world name. A missing world makes only its dependent lobby or arena unavailable; it is never replaced with an arbitrary fallback world.

## Commands

`/orionffa help` shows permission-aware commands and syntax. Major commands include `join <kit>`, `editkit <kit>`, `kit save|leave`, `menu`, `back`, `spectate <player>`, `party`, `arena`, `setlobby`, `seteditkit`, `reload`, `status`, and `debug`.

## Configuration

`config.yml` controls gameplay, kits, locations, arenas, party limits, combat timing, and storage mode. `messages.yml` contains MiniMessage text. `guis.yml` controls titles, rows, item appearance, slots, and registered GUI actions.

## Storage and integrations

YAML and MySQL providers persist statistics and per-player kit edits asynchronously. MySQL automatically falls back to YAML when its JDBC connection cannot be initialized. WorldEdit/FAWE reset integration is isolated; with a configured schematic and installed adapter, arena reset pastes the configured schematic. Scheduled resets only run for empty arenas. PlaceholderAPI can expose persistent statistics.

## License

GPL-3.0. The complete license text is included in `LICENSE`.

## Party matches

Party leaders can start a party-only match with `/orionffa party fight [kit]` or a two-team split match with `/orionffa party split [kit]`. All members must be online and outside FFA. Split matches require `split-spawns.team-a` and `split-spawns.team-b` on the selected arena. Friendly fire is blocked by team membership and the match owns its participant lifecycle.

Party membership is locked while a match is active. A disconnect ends the match and restores the remaining participants through the normal FFA state restoration path.

## Target build

The project targets Java 25 and Gradle 9.x. Build with `./gradlew clean build` on a machine with JDK 25 available. Optional integrations are detected at runtime and do not prevent the base plugin from starting when absent.
