# OrionFFA-Core

OrionFFA-Core is a configuration-first Free-For-All plugin for Paper 26.2.x / Java 25. The rewrite is built around explicit player state, configurable locations and arenas, safe arena reset/schematic handling, kit editing, party workflows, persistent statistics, and isolated optional integrations.

## Commands

The canonical command is **`/orionffa`** with the short alias **`/offa`**. There is no `/ofa` alias.

> **Lobby gate:** On a first install the lobby is intentionally unset. Until an admin runs `/offa setlobby`, OrionFFA gameplay/admin commands are blocked and no player is teleported to a fallback/default location. After the lobby is set, player commands only work in the lobby's configured world. `/offa setlobby` is the setup exception so an admin can configure or move the lobby from any world.

### Core commands

| Command | Description |
|---|---|
| `/offa` | Opens the command help. |
| `/offa help` | Shows permission-aware command help. |
| `/offa lobby` | Sends the player to the configured FFA lobby and applies the lobby menu. |
| `/offa join <kit>` | Joins the best available arena for the selected kit. |
| `/offa editkit <kit>` | Opens the selected kit for editing. |
| `/offa kit save [kit]` | Saves the player's edited kit. If the kit is omitted, the current edited kit is used. |
| `/offa kit leave` | Leaves kit editing and returns to the configured lobby. |
| `/offa leave` | Leaves FFA or finishes an active party match. |
| `/offa spectate <player>` | Starts spectating an eligible FFA player. |

### Admin and configuration commands

| Command | Description |
|---|---|
| `/offa setlobby` | Sets the current position as the FFA lobby. This is the required first-install setup command. |
| `/offa seteditkit` | Sets the current position as the kit-editing location. |
| `/offa reload` | Reloads plugin configuration, messages, kits, arenas, GUIs, and arena reset scheduling. |
| `/offa status` | Displays diagnostic counts for sessions, combat, parties, storage writes, and arenas. |
| `/offa debug` | Runs the diagnostic/status command. |
| `/offa force <player> <kit>` | Forces an online player into a kit/arena. |
| `/offa storage status` | Shows the active storage provider and pending writes. |
| `/offa storage migrate` | Migrates stored player data to the configured target provider when supported. |

### Arena commands

| Command | Description |
|---|---|
| `/offa arena create` | Starts the arena creation workflow, equips the selection wand, suspends the lobby hotbar, and adds the configurable cancel item. |
| `/offa arena save <name>` | Saves the active selection as an arena and stores its original schematic. The creator may stand inside the selected cuboid at the desired spawn. |
| `/offa arena list` | Lists configured arenas. |
| `/offa arena info <arena>` | Shows arena capacity, lock state, bound kit, and schematic path. |
| `/offa arena bind <arena> <kit>` | Binds an enabled kit to an arena. |
| `/offa arena lock <arena>` | Locks an arena so new players cannot join it. |
| `/offa arena unlock <arena>` | Unlocks an arena. |
| `/offa arena setspawn <arena>` | Sets the arena spawn to the admin's current position. |
| `/offa arena capacity <arena> <players>` | Changes an arena's maximum player capacity. |
| `/offa arena delete <arena>` | Deletes an empty arena and its saved schematic. |
| `/offa arena reset <arena>` | Resets an arena using its configured WorldEdit/FAWE schematic adapter. |

### Party commands

| Command | Description |
|---|---|
| `/offa party create` | Creates a party. |
| `/offa party invite <player>` | Invites an online player to the party. |
| `/offa party join <leader>` | Joins the specified party leader's party. |
| `/offa party kick <player>` | Removes a party member. |
| `/offa party promote <player>` | Promotes a party member. |
| `/offa party leave` | Leaves the current party. |
| `/offa party disband` | Disbands the party. |
| `/offa party chat` | Toggles party-only chat. |
| `/offa party fight [kit]` | Starts a party-only match using the optional kit. |
| `/offa party split [kit]` | Starts a two-team split match using the optional kit and configured split spawns. |

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `orionffa.use` | `true` | Base access for normal player commands. |
| `orionffa.join` | `true` | Declared permission for kit/FFA joining. |
| `orionffa.editkit` | `true` | Declared permission for kit editing. |
| `orionffa.spectate` | `true` | Declared permission for spectating. |
| `orionffa.party` | `true` | Declared permission for party commands. |
| `orionffa.admin` | `op` | Administrator access. |
| `orionffa.admin.*` | `op` | Administrator wildcard; grants the admin and declared admin child permissions below. |
| `orionffa.force` | inherited via `orionffa.admin.*` | Force a player into a kit. |
| `orionffa.setlobby` | inherited via `orionffa.admin.*` | Set the FFA lobby. |
| `orionffa.seteditkit` | inherited via `orionffa.admin.*` | Set the edit-kit location. |
| `orionffa.reload` | inherited via `orionffa.admin.*` | Reload plugin configuration. |
| `orionffa.status` | inherited via `orionffa.admin.*` | View diagnostics. |
| `orionffa.debug` | inherited via `orionffa.admin.*` | Access debug/status functionality. |
| `orionffa.arena` | inherited via `orionffa.admin.*` | Arena administration. |
| `orionffa.arena.create` | inherited via `orionffa.admin.*` | Start arena creation. |
| `orionffa.arena.save` | inherited via `orionffa.admin.*` | Save an arena selection. |
| `orionffa.arena.reset` | inherited via `orionffa.admin.*` | Reset an arena. |
| `orionffa.arena.list` | inherited via `orionffa.admin.*` | List arenas. |
| `orionffa.arena.info` | inherited via `orionffa.admin.*` | View arena details. |
| `orionffa.arena.bind` | inherited via `orionffa.admin.*` | Bind a kit to an arena. |
| `orionffa.arena.lock` | inherited via `orionffa.admin.*` | Lock an arena. |
| `orionffa.arena.unlock` | inherited via `orionffa.admin.*` | Unlock an arena. |
| `orionffa.arena.setspawn` | inherited via `orionffa.admin.*` | Set an arena spawn. |
| `orionffa.arena.capacity` | inherited via `orionffa.admin.*` | Change arena capacity. |
| `orionffa.arena.delete` | inherited via `orionffa.admin.*` | Delete an arena. |
| `orionffa.storage` | inherited via `orionffa.admin.*` | Access storage administration. |
| `orionffa.storage.migrate` | inherited via `orionffa.admin.*` | Run storage migration. |

The current command executor uses the base `orionffa.use` and `orionffa.admin` checks for execution; the more specific nodes are declared as the permission layout exposed by `plugin.yml`.

## First install

1. Start the server once. OrionFFA-Core creates its configuration files without inventing a lobby location.
2. Stand at the desired lobby location and run `/offa setlobby` as an admin.
3. If kit editing is needed, stand at the desired edit location and run `/offa seteditkit`.
4. Create arenas with `/offa arena create`, select the two corners, stand at the desired arena spawn, and run `/offa arena save <name>`.
5. Bind kits with `/offa arena bind <arena> <kit>` when an arena should be kit-specific.
6. Configure `config.yml`, `arenas.yml`, `guis.yml`, and `messages.yml` as needed, then use `/offa reload`.

The plugin never replaces an unset lobby with the default `world` or another arbitrary world.

## Updating an existing installation

Do **not** delete the entire `plugins/OrionFFA-Core` directory when updating. Keep persistent data such as `arenas.yml`, saved schematics, storage/statistics data, and your customized configuration. The new first-install behavior only applies when the lobby section is actually absent.

New configuration defaults are not blindly merged into an existing `config.yml`. When a new option is introduced, add it to an existing customized configuration as documented. The arena-selection cancel item is configurable under `arena-selection.cancel-item`.

## Arena creation workflow

`/offa arena create` temporarily removes OrionFFA lobby hotbar items and gives the configured selection wand plus a cancel item. The cancel item is configurable in `config.yml`:

```yaml
arena-selection:
  mode: automatic
  strategy: least-occupied
  cancel-item:
    material: BARRIER
    amount: 1
    slot: 8
    name: "<red>Cancel Arena Selection</red>"
    lore:
      - "<gray>Click to cancel arena creation."
```

Clicking the cancel item ends the active creation workflow, removes the temporary selection tool/item, and restores the lobby hotbar. Reconnecting while a selection is active also cancels the unfinished workflow. `/offa arena save <name>` requires an active `/offa arena create` session.

## Configuration

`config.yml` controls gameplay, combat timing, arena selection, arena reset scheduling, party limits, split-match settings, storage type, and built-in kit definitions. `arenas.yml` contains arena definitions and reset metadata. `messages.yml` contains MiniMessage text. `guis.yml` controls GUI presentation and actions.

The lobby and edit-kit locations are optional configuration sections. The lobby must be explicitly set with `/offa setlobby` before normal OrionFFA commands are usable.

## Storage and integrations

YAML and MySQL providers persist statistics and per-player kit edits asynchronously. MySQL automatically falls back to YAML when its JDBC connection cannot be initialized. WorldEdit/FAWE reset integration is isolated; with a configured schematic and installed adapter, arena reset pastes the configured schematic. Scheduled resets only run for empty arenas. PlaceholderAPI can expose persistent statistics.

## Build

Use Java 25 and Gradle 9.x. The target Paper API is `26.2.build.48-alpha` and the project targets Java 25.

```text
./gradlew clean build
```

The Paper API is `compileOnly`; the resulting plugin JAR does not bundle the server API.

## Project goals

The rewrite emphasizes authoritative player state, centralized teleports, safe asynchronous storage/schematic operations, atomic arena reservations, reliable spectator/recovery transitions, configuration-first gameplay, and optional integrations that fail gracefully when unavailable.

## License

GPL-3.0. The complete license text is included in `LICENSE`.
