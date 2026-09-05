package com.karlo.orionffa.statistics;

import com.karlo.orionffa.storage.StorageProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class StatisticsManager {
    private final JavaPlugin plugin;
    private final StorageProvider storage;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final AtomicInteger pendingWrites = new AtomicInteger();

    public StatisticsManager(JavaPlugin plugin, StorageProvider storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load(UUID playerId) {
        Entry entry = entries.computeIfAbsent(playerId, ignored -> new Entry());
        storage.loadStatistics(playerId).whenComplete((loaded, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().warning("Could not load statistics for " + playerId + ": " + failure.getMessage());
                entry.loaded = true;
                return;
            }
            entry.statistics = apply(entry.pending, loaded);
            entry.pending = PlayerStatistics.EMPTY;
            entry.loaded = true;
            if (entry.dirty) save(playerId, entry);
        }));
    }

    public PlayerStatistics get(UUID playerId) {
        return entries.getOrDefault(playerId, new Entry()).statistics;
    }

    public void recordKill(UUID playerId) {
        mutate(playerId, PlayerStatistics::kill);
    }

    public void recordDeath(UUID playerId) {
        mutate(playerId, PlayerStatistics::death);
    }

    public int pendingWrites() {
        return pendingWrites.get();
    }

    public void flush() {
        entries.forEach(this::save);
    }

    public void unload(UUID playerId) {
        Entry entry = entries.get(playerId);
        if (entry != null && entry.loaded) save(playerId, entry);
    }

    private void mutate(UUID playerId, java.util.function.UnaryOperator<PlayerStatistics> operation) {
        Entry entry = entries.computeIfAbsent(playerId, ignored -> new Entry());
        if (entry.loaded) entry.statistics = operation.apply(entry.statistics);
        else entry.pending = operation.apply(entry.pending);
        entry.dirty = true;
    }

    private void save(UUID playerId, Entry entry) {
        if (!entry.loaded || !entry.dirty || entry.saving) return;
        entry.dirty = false;
        entry.saving = true;
        PlayerStatistics snapshot = entry.statistics;
        pendingWrites.incrementAndGet();
        storage.saveStatistics(playerId, snapshot).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            pendingWrites.decrementAndGet();
            entry.saving = false;
            if (failure != null) {
                entry.dirty = true;
                plugin.getLogger().warning("Could not save statistics for " + playerId + ": " + failure.getMessage());
            }
            if (entry.dirty) save(playerId, entry);
        }));
    }

    private static PlayerStatistics apply(PlayerStatistics delta, PlayerStatistics base) {
        PlayerStatistics result = base;
        for (int i = 0; i < delta.kills(); i++) result = result.kill();
        for (int i = 0; i < delta.deaths(); i++) result = result.death();
        return new PlayerStatistics(result.kills(), result.deaths(), delta.killStreak() == 0 ? result.killStreak() : delta.killStreak(),
                Math.max(result.bestKillStreak(), delta.bestKillStreak()));
    }

    private static final class Entry {
        private PlayerStatistics statistics = PlayerStatistics.EMPTY;
        private PlayerStatistics pending = PlayerStatistics.EMPTY;
        private boolean loaded;
        private boolean dirty;
        private boolean saving;
    }
}
