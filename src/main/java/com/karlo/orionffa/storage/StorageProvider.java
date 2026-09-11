package com.karlo.orionffa.storage;

import com.karlo.orionffa.statistics.PlayerStatistics;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StorageProvider extends AutoCloseable {
    CompletableFuture<PlayerKitData> loadKit(UUID playerId, String kitId);
    CompletableFuture<Void> saveKit(UUID playerId, String kitId, PlayerKitData data);
    /** Deletes a player preference; the default implementation writes an empty preference for compatibility. */
    default CompletableFuture<Void> deleteKit(UUID playerId, String kitId) {
        return saveKit(playerId, kitId, new PlayerKitData(new org.bukkit.inventory.ItemStack[0], new org.bukkit.inventory.ItemStack[0], null));
    }
    CompletableFuture<PlayerStatistics> loadStatistics(UUID playerId);
    CompletableFuture<Void> saveStatistics(UUID playerId, PlayerStatistics statistics);
    default String type(){return "unknown";}
    @Override void close();
}
