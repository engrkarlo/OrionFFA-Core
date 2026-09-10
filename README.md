# OrionFFA-Core

OrionFFA-Core is a Free-For-All plugin for Paper 26.2.x / Java 25, providing FFA gameplay, kits, arenas, combat, parties, spectating, and configurable GUIs.

## Commands

The main command is `/orionffa` with `/offa` as its short alias.

### Player commands

| Command | Description |
|---|---|
| `/offa` | Open command help. |
| `/offa help` | Show available commands. |
| `/offa lobby` | Return to the configured FFA lobby. |
| `/offa join <kit>` | Join FFA with a kit. |
| `/offa editkit <kit>` | Open a kit for editing. |
| `/offa kit save [kit]` | Save the current kit edit. |
| `/offa kit leave` | Leave kit editing. |
| `/offa leave` | Leave FFA or an active match. |
| `/offa spectate <player>` | Spectate an eligible player. |

### Admin commands

| Command | Description |
|---|---|
| `/offa setlobby` | Set the FFA lobby location. |
| `/offa seteditkit` | Set the kit-editing location. |
| `/offa reload` | Reload plugin configuration. |
| `/offa status` | Show plugin status. |
| `/offa debug` | Show diagnostic information. |
| `/offa force <player> <kit>` | Force a player into FFA. |

### Arena commands

| Command | Description |
|---|---|
| `/offa arena create` | Start arena creation. |
| `/offa arena save <name>` | Save the current arena selection. |
| `/offa arena list` | List configured arenas. |
| `/offa arena info <arena>` | Show arena information. |
| `/offa arena bind <arena> <kit>` | Bind a kit to an arena. |
| `/offa arena lock <arena>` | Lock an arena. |
| `/offa arena unlock <arena>` | Unlock an arena. |
| `/offa arena setspawn <arena>` | Set an arena spawn. |
| `/offa arena capacity <arena> <players>` | Set arena capacity. |
| `/offa arena delete <arena>` | Delete an arena. |
| `/offa arena reset <arena>` | Reset an arena from its schematic. |

### Party commands

| Command | Description |
|---|---|
| `/offa party create` | Create a party. |
| `/offa party invite <player>` | Invite a player. |
| `/offa party join <leader>` | Join a party. |
| `/offa party kick <player>` | Remove a party member. |
| `/offa party promote <player>` | Promote a party member. |
| `/offa party leave` | Leave a party. |
| `/offa party disband` | Disband a party. |
| `/offa party chat` | Toggle party chat. |
| `/offa party fight [kit]` | Start a party fight. |
| `/offa party split [kit]` | Start a split match. |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `orionffa.use` | true | Use OrionFFA commands. |
| `orionffa.join` | true | Join FFA. |
| `orionffa.editkit` | true | Edit kits. |
| `orionffa.spectate` | true | Spectate players. |
| `orionffa.party` | true | Use party features. |
| `orionffa.admin` | op | Administrator access. |
| `orionffa.admin.*` | op | All administrator permissions. |
| `orionffa.force` | admin | Force players into FFA. |
| `orionffa.setlobby` | admin | Set the FFA lobby. |
| `orionffa.seteditkit` | admin | Set the kit-edit location. |
| `orionffa.reload` | admin | Reload configuration. |
| `orionffa.status` | admin | View status information. |
| `orionffa.debug` | admin | View diagnostic information. |
| `orionffa.arena` | admin | Arena administration. |
| `orionffa.arena.*` | admin | Arena administration permissions. |

## Configuration

OrionFFA-Core uses the following configuration files:

| File | Purpose |
|---|---|
| `config.yml` | Gameplay and plugin settings. |
| `arenas.yml` | Arena definitions. |
| `guis.yml` | GUI configuration. |
| `messages.yml` | Plugin messages. |

The FFA lobby is configured with `/offa setlobby`. Other locations and gameplay settings can be configured through the plugin configuration.

## Integrations

Optional integrations currently include:

- WorldEdit / FAWE for arena schematic operations.
- PlaceholderAPI for placeholders.

## Building

Requires Java 25 and the Gradle wrapper included with the project.

```bash
./gradlew clean build
```

## Contributing

Keep changes focused and consistent with the existing architecture. Gameplay state should remain in its owning service, teleports should use `TeleportService`, and Bukkit player/entity APIs should not be called from asynchronous storage or background callbacks.

Before opening a pull request:

1. Build the project with `./gradlew clean build`.
2. Test the affected command, GUI, or gameplay flow on the target Paper version when practical.
3. Keep configuration, messages, permissions, and command completion in sync with code changes.
4. Avoid unrelated refactors in feature or bug-fix changes.
5. Include a clear description of what changed and how it was tested.

For larger changes, discuss the intended design before implementing a broad subsystem rewrite.

## License

GPL-3.0. See `LICENSE` for the full license text.
