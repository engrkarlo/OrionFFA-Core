# Contributing

Keep gameplay state in its owning service, route teleports through `TeleportService`, and never call Bukkit player or entity APIs from storage callbacks. Test the relevant command and GUI flow on the target Paper server before submitting a change.
