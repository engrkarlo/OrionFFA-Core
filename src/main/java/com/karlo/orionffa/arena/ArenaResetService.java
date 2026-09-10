package com.karlo.orionffa.arena;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArenaResetService {
    private final JavaPlugin plugin;
    private final ArenaManager arenas;
    private final SchematicService schematicService;
    private final ConcurrentMap<String, Boolean> resetting = new ConcurrentHashMap<>();

    public ArenaResetService(JavaPlugin plugin, ArenaManager arenas, SchematicService schematicService) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.schematicService = schematicService;
    }

    public boolean isResetting(String id) {
        return resetting.containsKey(normalize(id));
    }

    public CompletableFuture<ResetResult> reset(String id) {
        Optional<Arena> found = arenas.get(id);
        if (found.isEmpty()) return CompletableFuture.completedFuture(ResetResult.failure("arena-unavailable"));

        Arena arena = found.get();
        String arenaId = normalize(arena.id());
        if (arena.occupants() > 0) return CompletableFuture.completedFuture(ResetResult.failure("arena-occupied"));
        if (schematicService == null) {
            return CompletableFuture.completedFuture(ResetResult.failure("reset-adapter-missing"));
        }
        if (!plugin.getConfig().getBoolean("arena-reset.enabled", true)) {
            return CompletableFuture.completedFuture(ResetResult.failure("reset-disabled"));
        }

        // putIfAbsent returns null when this call successfully claims the reset.
        if (resetting.putIfAbsent(arenaId, Boolean.TRUE) != null) {
            return CompletableFuture.completedFuture(ResetResult.failure("reset-busy"));
        }

        String configuredPath = plugin.getConfig().getString("arena-reset.arenas." + arena.id() + ".schematic", "").trim();
        FileResolution resolution = resolveSchematic(arena.id(), configuredPath);
        if (resolution.file() == null) {
            resetting.remove(arenaId);
            if (configuredPath.isBlank()) {
                plugin.getLogger().warning("No reset schematic is configured for arena '" + arena.id()
                        + "'. Configure arena-reset.arenas." + arena.id() + ".schematic or place a file at "
                        + resolution.expectedPath().getAbsolutePath());
                return CompletableFuture.completedFuture(ResetResult.failure("reset-schematic-not-configured"));
            }
            plugin.getLogger().warning("Arena schematic does not exist: " + resolution.expectedPath().getAbsolutePath());
            return CompletableFuture.completedFuture(ResetResult.failure("reset-schematic-missing"));
        }

        File file = resolution.file();
        // Resolve the Bukkit world while still on the server thread. The resolved Location
        // is then passed into the FAWE async operation without touching Bukkit lookup APIs there.
        Optional<Location> resolvedTarget = arena.spawn().resolve();
        if (resolvedTarget.isEmpty()) {
            resetting.remove(arenaId);
            return CompletableFuture.completedFuture(ResetResult.failure("reset-world-unavailable"));
        }
        Location target = resolvedTarget.get();

        CompletableFuture<ResetResult> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            if (!file.isFile()) throw new IllegalStateException("Schematic disappeared during reset: " + file);
        }).thenRun(() -> {
            if (schematicService.asyncCapable()) {
                CompletableFuture.runAsync(() -> paste(file, target, arenaId, arena, result));
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> paste(file, target, arenaId, arena, result));
            }
        }).exceptionally(e -> {
            resetting.remove(arenaId);
            result.complete(ResetResult.failure("reset-failed"));
            return null;
        });
        return result;
    }

    private FileResolution resolveSchematic(String arenaId, String configuredPath) {
        if (!configuredPath.isBlank()) {
            return new FileResolution(new File(plugin.getDataFolder(), configuredPath),
                    new File(plugin.getDataFolder(), configuredPath));
        }

        File schem = new File(plugin.getDataFolder(), "schematics/" + arenaId + ".schem");
        if (schem.isFile()) return new FileResolution(schem, schem);

        File schematic = new File(plugin.getDataFolder(), "schematics/" + arenaId + ".schematic");
        if (schematic.isFile()) return new FileResolution(schematic, schematic);

        return new FileResolution(null, schem);
    }

    private void paste(File file, Location target, String arenaId, Arena arena, CompletableFuture<ResetResult> result) {
        try {
            schematicService.paste(file, target);
            result.complete(ResetResult.success("arena-reset"));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Arena reset failed for " + arena.id() + " using " + file.getAbsolutePath(), e);
            result.complete(ResetResult.failure("reset-failed"));
        } finally {
            resetting.remove(arenaId);
        }
    }

    private static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private record FileResolution(File file, File expectedPath) { }

    public record ResetResult(boolean success, String messageKey) {
        static ResetResult success(String k) { return new ResetResult(true, k); }
        static ResetResult failure(String k) { return new ResetResult(false, k); }
    }
}
