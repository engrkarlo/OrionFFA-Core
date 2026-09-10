package com.karlo.orionffa.arena;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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

    public boolean isResetting(String id) { return resetting.containsKey(normalize(id)); }

    public CompletableFuture<ResetResult> reset(String id) {
        Optional<Arena> found = arenas.get(id);
        if (found.isEmpty()) return CompletableFuture.completedFuture(ResetResult.failure("arena-unavailable"));
        Arena arena = found.get();
        String arenaId = normalize(arena.id());

        if (arenas.hasPlayersInside(arena)) return CompletableFuture.completedFuture(ResetResult.failure("arena-occupied"));
        if (schematicService == null) return CompletableFuture.completedFuture(ResetResult.failure("reset-adapter-missing"));
        if (!plugin.getConfig().getBoolean("arena-reset.enabled", true)) return CompletableFuture.completedFuture(ResetResult.failure("reset-disabled"));
        if (resetting.putIfAbsent(arenaId, Boolean.TRUE) != null) return CompletableFuture.completedFuture(ResetResult.failure("reset-busy"));

        File file = arenas.schematicFile(arena);
        if (!file.isFile()) {
            resetting.remove(arenaId);
            plugin.getLogger().warning("Arena reset schematic does not exist for '" + arena.id() + "': " + file.getAbsolutePath());
            return CompletableFuture.completedFuture(ResetResult.failure("reset-schematic-missing"));
        }

        Optional<Location> resolvedTarget = arenas.resetTarget(arena);
        if (resolvedTarget.isEmpty()) {
            resetting.remove(arenaId);
            return CompletableFuture.completedFuture(ResetResult.failure("reset-world-unavailable"));
        }
        Location target = resolvedTarget.get();
        CompletableFuture<ResetResult> result = new CompletableFuture<>();

        Runnable paste = () -> {
            if (arenas.hasPlayersInside(arena)) {
                resetting.remove(arenaId);
                result.complete(ResetResult.failure("arena-occupied"));
                return;
            }
            try {
                schematicService.paste(file, target);
                result.complete(ResetResult.success("arena-reset"));
            } catch (Exception exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Arena reset failed for " + arena.id() + " using " + file.getAbsolutePath(), exception);
                result.complete(ResetResult.failure("reset-failed"));
            } finally {
                resetting.remove(arenaId);
            }
        };

        if (schematicService.asyncCapable()) CompletableFuture.runAsync(paste);
        else plugin.getServer().getScheduler().runTask(plugin, paste);
        return result;
    }

    private static String normalize(String id) { return id.toLowerCase(Locale.ROOT); }

    public record ResetResult(boolean success, String messageKey) {
        static ResetResult success(String k) { return new ResetResult(true, k); }
        static ResetResult failure(String k) { return new ResetResult(false, k); }
    }
}
