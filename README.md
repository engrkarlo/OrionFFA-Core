# OrionFFACore

**Native Paper FFA core for Paper 26.2**  
**Author:** Karlow  
**Version:** 2.3.0

OrionFFACore is a native Java replacement for the original FFA Skript. It is designed around safe synchronous Bukkit/Paper player operations, asynchronous database I/O, and asynchronous FAWE arena work. It includes configurable FFA kits, parties, split matches, inventory isolation, persistent custom kits, per-world statistics, PlaceholderAPI integration, and native respawn/pose recovery.

> **Runtime:** Paper 26.2 requires a Java 25 runtime. The plugin itself is compiled for Java 21 compatibility.

---

## Features

### FFA
- `/ffa join`
- `/ffa back`
- `/ffa menu`
- `/ffa editkit`
- `/ffa force <player>`
- `/ffa setlobby`
- `/ffa seteditkit`
- `/ffa reload`
- Configurable FFA world.
- Configurable kit locations.
- Selector hotbar items with private PersistentDataContainer tags.
- Command protection while actively playing FFA.
- Safe-zone pushback and combat tagging.

### Default kits
The included defaults mirror the original FFA setup:

- nethpot
- diapot
- axe
- maceht
- macelt
- spearmace
- smpkit
- uhc
- crystalffa
- opduel
- cart
- elymace
- sword

Each kit is configurable in `config.yml`, including display name, icon, arena location, and give command.

### Parties
- `/party gui`
- `/party invite <player>`
- `/party join <leader>`
- `/party leave`
- `/party disband`
- `/party kick <player>`
- `/party promote <player>`
- `/party chat`
- `/party fights`
- `/party split`
- Configurable maximum party size.

### Split matches
- Random team assignment.
- Configurable split arena spawns.
- Friendly-fire protection.
- Arena locking to prevent two matches using the same split arena.
- Automatic cleanup when matches finish.
- Optional FAWE reset after split matches.

### Inventory handling
OrionFFACore supports three inventory modes:

```yaml
inventory:
  mode: multiverse-inventories
```

#### `multiverse-inventories`
Recommended when Multiverse-Inventories is installed.

OrionFFACore does **not** maintain a competing cross-world inventory snapshot. Multiverse-Inventories remains the authority for world/group inventory switching. This prevents two systems from fighting over inventory state.

Configure your FFA world in the same Multiverse-Inventories group that should contain the FFA inventory.

#### `native`
OrionFFACore captures the player's state before FFA and restores it when they leave. The native snapshot protects:

- Main inventory
- Armor
- Offhand
- Ender chest
- Cursor item
- XP
- Level
- Health
- Food
- Saturation
- Game mode
- Flight permission/state

The snapshot is also restored when a player quits while actively inside FFA.

#### `none`
No inventory restoration is performed. Use this only if another plugin owns inventory management.

### GUI anti-dupe protection
FFA GUI inventories are intentionally non-storage interfaces.

OrionFFACore protects them against:

- Normal clicks
- Shift-clicks
- Number-key hotbar swaps
- Double-click item collection
- Dragging
- Inventory transfers
- Selector-item collisions

GUI and selector items use private plugin metadata instead of relying only on display names.

### Custom kits
Players can edit their personal kit configuration with:

```text
/ffa editkit
/kit save
/kit leave
```

Custom kit data can be stored in YAML or MySQL.

---

# Statistics

OrionFFACore tracks statistics **per world**.

Every statistic record is keyed by:

```text
player UUID + world name
```

This means kills in `ffa` do not become kills in another world.

Tracked values:

- Kills
- Deaths
- Current killstreak
- Best killstreak
- KD
- Best KD

A death resets the current streak but does not remove the best streak.

KD is calculated as:

```text
kills / deaths
```

When deaths are zero, KD is represented by the current kill count. The minimum number of kills required before updating Best KD is configurable.

---

# PlaceholderAPI

PlaceholderAPI is optional. If installed, OrionFFACore automatically registers the expansion:

```text
orionffa
```

No separate expansion jar is required.

Enable/disable it with:

```yaml
placeholders:
  enabled: true
  identifier: orionffa
```

## Current-world placeholders

These are the recommended placeholders for TAB, FancyHolograms, AJLeaderboards, scoreboards, holograms, etc. They always use the **player's current world**:

```text
%orionffa_kills%
%orionffa_deaths%
%orionffa_killstreak%
%orionffa_bestkillstreak%
%orionffa_kd%
%orionffa_bestkd%
%orionffa_world%
%orionffa_world_safe%
```

Examples:

```text
Kills: %orionffa_kills%
Deaths: %orionffa_deaths%
K/D: %orionffa_kd%
Streak: %orionffa_killstreak%
Best Streak: %orionffa_bestkillstreak%
Best K/D: %orionffa_bestkd%
```

## Specific-world placeholders

You can also request a particular world by adding its world name to the placeholder:

```text
%orionffa_kills_ffa%
%orionffa_deaths_ffa%
%orionffa_killstreak_ffa%
%orionffa_bestkillstreak_ffa%
%orionffa_kd_ffa%
%orionffa_bestkd_ffa%
```

World names containing spaces can use underscores when referenced by the placeholder.

The expansion resolves the suffix against loaded Bukkit worlds, so the placeholder does not accidentally create statistics for a misspelled world name.

### TAB
Use the normal PlaceholderAPI placeholder syntax in your TAB configuration:

```text
Kills: %orionffa_kills%
Deaths: %orionffa_deaths%
K/D: %orionffa_kd%
```

### FancyHolograms
Use the same placeholders in the hologram text:

```text
&6✦ &eᴏʀɪᴏɴ ꜰꜰᴀ
&fKills: &a%orionffa_kills%
&fDeaths: &c%orionffa_deaths%
&fK/D: &b%orionffa_kd%
```

### AJLeaderboards
The statistics placeholders return numeric strings, making them suitable for leaderboard systems that consume PlaceholderAPI values.

For an FFA-world leaderboard, use the current-world placeholders:

```text
%orionffa_kills%
%orionffa_deaths%
%orionffa_kd%
```

If the leaderboard plugin evaluates a placeholder with a player context, it will read that player's statistics for their current world.

For a fixed loaded world, use the explicit-world form:

```text
%orionffa_kills_ffa%
```

---

# Statistics storage

Statistics follow the selected storage mode.

## YAML

```yaml
storage:
  mode: yaml
```

Statistics are stored in:

```text
plugins/OrionFFACore/stats.yml
```

## MySQL

```yaml
storage:
  mode: mysql
```

The plugin creates a dedicated statistics table automatically:

```text
<configured-prefix>stats
```

Default:

```text
orionffa_stats
```

The table uses `(uuid, world)` as its primary key.

Database reads/writes run through OrionFFACore's asynchronous storage executor. Bukkit player operations remain on the main server thread.

---

# MySQL configuration

```yaml
storage:
  mode: mysql
  mysql:
    host: 127.0.0.1
    port: 3306
    database: minecraft
    username: root
    password: "CHANGE_ME"
    use-ssl: false
    worker-threads: 2
    selection-retry-ticks: 4
    fallback-to-yaml: true
    create-database: true
    table-prefix: orionffa_
```

The same MySQL connection is used for custom kits and statistics.

Check storage status with:

```text
/ffa storage status
```

Existing YAML custom kits can be migrated with:

```text
/ffa storage migrate
```

---

# FAWE arena resets

OrionFFACore can save WorldEdit/FAWE selections as `.schem` files and restore them asynchronously.

## 1. Select the arena

Use FAWE/WorldEdit:

```text
//wand
//pos1
//pos2
```

Select the complete arena region.

## 2. Save the schematic

```text
/ffa arena save nethpot-1
```

The selection is saved into:

```text
plugins/OrionFFACore/schematics/nethpot-1.schem
```

The selected minimum point becomes the default paste target, so the schematic can be restored to its original location.

## 3. Configure scheduled resets

```yaml
arena-reset:
  enabled: true
  schedule:
    enabled: true
    interval-seconds: 300
    run-on-startup: false
  defaults:
    scheduled: true
```

Each arena can independently opt into scheduled resets:

```yaml
arena-reset:
  arenas:
    nethpot-1:
      enabled: true
      scheduled: true
      schematic: nethpot-1.schem
      target:
        world: ffa
        x: 125.0
        y: -60.0
        z: 376.0
```

Set `scheduled: false` to keep an arena available for manual resets without putting it on the timer.

Manual commands:

```text
/ffa arena list
/ffa arena reset <id>
```

The scheduled reset task only selects entries where both `enabled` and `scheduled` are true.

---

# Respawn and avatar desync recovery

OrionFFACore contains native Paper recovery logic rather than attempting to solve the unusual death/pose issue with `/kill` commands.

After respawn it can:

- Force the player to `STANDING` pose.
- Reset sneaking.
- Reset sprinting.
- Reset gliding.
- Reset riptiding.
- Reset flying.
- Clear the active item.
- Leave vehicles.
- Zero velocity.
- Resend entity data to the affected player using Paper internals when available.
- Repeat recovery on multiple ticks.

Configured recovery passes:

```yaml
recovery:
  recovery-ticks: [1, 2, 4, 8, 12]
```

No automated `/kill` command is used.

---

# Performance model

OrionFFACore is intentionally **not** “everything async.” Bukkit/Paper player and world APIs must be used on the server thread unless an API explicitly supports asynchronous access.

The plugin instead separates work correctly:

### Main thread
- Player inventory changes
- Teleports
- Game modes
- Player state
- GUI operations
- Event handling
- Placeholder reads from cached statistics

### Async workers
- MySQL connections
- MySQL custom-kit loading/saving
- MySQL statistic loading/saving
- FAWE schematic reset queue
- YAML statistic saves

This avoids blocking the Paper tick thread with database I/O or large reset operations while avoiding unsafe asynchronous Bukkit calls.

---

# Commands

| Command | Purpose |
|---|---|
| `/ffa join` | Enter FFA |
| `/ffa back` | Leave FFA and restore inventory according to configured mode |
| `/ffa menu` | Open kit menu |
| `/ffa editkit` | Open custom kit editor |
| `/ffa force <player>` | Force a player out of FFA |
| `/ffa setlobby` | Save FFA lobby |
| `/ffa seteditkit` | Save kit editing location |
| `/ffa reload` | Reload configuration |
| `/ffa reset <arena-id>` | Manually reset an arena |
| `/ffa arena save <id>` | Save a WorldEdit/FAWE selection as a schematic |
| `/ffa arena reset <id>` | Reset a saved arena |
| `/ffa arena list` | List reset arenas |
| `/ffa storage status` | Show storage status |
| `/ffa storage migrate` | Migrate YAML custom kits to MySQL |
| `/ffa stats <player> [world]` | View per-world statistics |
| `/party` | Open party GUI |
| `/party invite <player>` | Invite a player |
| `/party join <leader>` | Join an invited party |
| `/party leave` | Leave a party |
| `/party disband` | Disband your party |
| `/party kick <player>` | Kick a member |
| `/party promote <player>` | Promote a member |
| `/party chat` | Toggle party chat |
| `/party fights` | Open party fights |
| `/party split` | Start split selection |
| `/kit save` | Save an edited kit |
| `/kit leave` | Leave kit editing |
| `/setsplitspawn <kit> <A\|B> <arena> [max]` | Configure split spawn |
| `/ffasafezone pos1` | Save safezone corner 1 |
| `/ffasafezone pos2` | Save safezone corner 2 |
| `/ffasafezone status` | View safezone |
| `/ffasafezone remove` | Remove safezone |

---

# Permissions

```text
orionffa.use
orionffa.admin
```

Legacy aliases are also recognized:

```text
ffa.use
ffa.admin
```

---

# Dependencies / integrations

### Required
- Paper 26.2+

### Optional
- Multiverse-Inventories
- FastAsyncWorldEdit
- WorldEdit
- PlayerKits
- PlaceholderAPI
- MySQL/MariaDB-compatible server

PlayerKits is only required if your configured kit `give.command` uses PlayerKits commands.

FAWE is required only for schematic reset functionality.

PlaceholderAPI is required only when external plugins need OrionFFACore placeholders.

---

# Installation

1. Stop the server.
2. Put `OrionFFACore-2.3.0-Paper-26.2.jar` in `plugins/`.
3. Install/configure the optional integrations you use.
4. Start the server.
5. Configure `plugins/OrionFFACore/config.yml`.
6. Run `/ffa setlobby`.
7. Run `/ffa seteditkit`.
8. Verify the FFA kit locations.
9. If using FAWE resets, create the schematics and configure `arena-reset.arenas`.
10. If using MySQL, set `storage.mode: mysql` and credentials before allowing players to save kits.

A full server restart is recommended after changing dependencies or replacing the plugin JAR.

---

# Configuration philosophy

OrionFFACore intentionally keeps gameplay values in `config.yml` instead of hard-coding the old Skript behavior.

The following are configurable:

- FFA world
- Permissions
- Inventory mode
- Multiverse behavior
- MySQL settings
- Storage workers
- Lobby/edit locations
- Combat timers
- Killer credit
- Crystal credit
- Party size
- Split size
- Split arenas
- Selector items
- GUI titles
- GUI slots
- Kit names/icons/locations/commands
- Recovery behavior
- Recovery timing
- Chat prefix
- Small-caps formatting
- Messages
- Placeholder identifier
- Statistic behavior
- FAWE reset schedule
- Per-arena reset participation
- FAWE paste behavior

---

# Small-caps chat

OrionFFACore supports the stylized Unicode small-caps format used by the original polished chat design.

```yaml
messages:
  small-caps:
    enabled: true
    skip-commands: true
```

The formatter is intended for decorative chat text. Commands themselves are never rewritten.

---

# Notes for production servers

- Use Multiverse-Inventories if your server already uses it for world-specific inventories; leave OrionFFACore on `multiverse-inventories` mode.
- Keep FAWE resets asynchronous and avoid scheduling many huge schematic pastes at the exact same tick.
- Keep MySQL on a local/low-latency network where possible.
- Back up `config.yml`, `custom-kits.yml`, `stats.yml`, schematics, and the MySQL database before major migrations.
- Replace the generated default MySQL password immediately.
- Test unusual respawn scenarios on a staging server before enabling the FFA world for players.

---

# License

OrionFFACore is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

Copyright (C) 2026 Karlow.

See `LICENSE` for the complete license terms.
