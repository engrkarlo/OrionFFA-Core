package com.karlo.orionffa.storage;

import com.karlo.orionffa.statistics.PlayerStatistics;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class YamlStorageProvider implements StorageProvider {
    private final File directory;
    private final ExecutorService executor;

    public YamlStorageProvider(JavaPlugin plugin) {
        directory = new File(plugin.getDataFolder(), "playerdata");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot create playerdata directory");
        executor = Executors.newSingleThreadExecutor(named("OrionFFA-Storage"));
    }

    @Override
    public CompletableFuture<PlayerKitData> loadKit(UUID playerId, String kitId) {
        return CompletableFuture.supplyAsync(() -> {
            YamlConfiguration file = YamlConfiguration.loadConfiguration(kitFile(playerId, kitId));
            return new PlayerKitData(
                    file.getList("inventory").stream().map(value -> value instanceof org.bukkit.inventory.ItemStack item ? item : null).toArray(org.bukkit.inventory.ItemStack[]::new),
                    file.getList("armor").stream().map(value -> value instanceof org.bukkit.inventory.ItemStack item ? item : null).toArray(org.bukkit.inventory.ItemStack[]::new),
                    file.getItemStack("offhand"));
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveKit(UUID playerId, String kitId, PlayerKitData data) {
        return CompletableFuture.runAsync(() -> {
            YamlConfiguration file = new YamlConfiguration();
            file.set("inventory", java.util.Arrays.asList(data.inventory()));
            file.set("armor", java.util.Arrays.asList(data.armor()));
            file.set("offhand", data.offhand());
            try {
                file.save(kitFile(playerId, kitId));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot save kit " + kitId + " for " + playerId, exception);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<PlayerStatistics> loadStatistics(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            YamlConfiguration file = YamlConfiguration.loadConfiguration(file(playerId));
            return new PlayerStatistics(file.getInt("kills"), file.getInt("deaths"), file.getInt("kill-streak"), file.getInt("best-kill-streak"));
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveStatistics(UUID playerId, PlayerStatistics statistics) {
        return CompletableFuture.runAsync(() -> {
            YamlConfiguration file = new YamlConfiguration();
            file.set("kills", statistics.kills());
            file.set("deaths", statistics.deaths());
            file.set("kill-streak", statistics.killStreak());
            file.set("best-kill-streak", statistics.bestKillStreak());
            try {
                file.save(file(playerId));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot save statistics for " + playerId, exception);
            }
        }, executor);
    }

    @Override public String type(){return "yaml";}

    @Override
    public void close() {
        executor.shutdown();
    }

    private File file(UUID playerId) {
        return new File(directory, playerId + ".yml");
    }

    private File kitFile(UUID playerId, String kitId) {
        File directory = new File(this.directory, playerId + "/kits");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot create kit directory");
        return new File(directory, kitId.toLowerCase(java.util.Locale.ROOT) + ".yml");
    }

    private static ThreadFactory named(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
