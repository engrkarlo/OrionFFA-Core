package com.karlo.orionffa.kit;

import com.karlo.orionffa.storage.PlayerKitData;
import com.karlo.orionffa.storage.StorageProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KitPersistenceManager {
    private final JavaPlugin plugin;
    private final StorageProvider storage;
    private final Map<UUID, Map<String, PlayerKitData>> cache = new ConcurrentHashMap<>();
    private final Set<String> defaultNext = ConcurrentHashMap.newKeySet();

    public KitPersistenceManager(JavaPlugin plugin, StorageProvider storage) { this.plugin = plugin; this.storage = storage; }

    public void load(UUID playerId, Iterable<KitDefinition> definitions) {
        for (KitDefinition definition : definitions) {
            storage.loadKit(playerId, definition.id()).whenComplete((data, failure) -> {
                if (failure != null) { plugin.getLogger().warning("Could not load custom kit " + definition.id() + " for " + playerId + ": " + failure.getMessage()); return; }
                if (data.inventory().length == 0 && data.armor().length == 0 && data.offhand() == null) return;
                cache.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(definition.id(), data);
            });
        }
    }

    public Optional<PlayerKitData> find(UUID playerId, String kitId) {
        String id = kitId.toLowerCase(java.util.Locale.ROOT);
        if (defaultNext.remove(key(playerId, id))) return Optional.empty();
        return Optional.ofNullable(cache.getOrDefault(playerId, Map.of()).get(id));
    }

    /** Makes the next kit application for this player/kit use the admin definition. */
    public void useDefaultOnce(UUID playerId, String kitId) { defaultNext.add(key(playerId, kitId.toLowerCase(java.util.Locale.ROOT))); }

    public void saveFromPlayer(Player player, String kitId) {
        PlayerKitData data = new PlayerKitData(player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand());
        String id = kitId.toLowerCase(java.util.Locale.ROOT);
        cache.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(id, data);
        storage.saveKit(player.getUniqueId(), id, data).whenComplete((ignored, failure) -> { if (failure != null) plugin.getLogger().warning("Could not save custom kit " + id + " for " + player.getUniqueId() + ": " + failure.getMessage()); });
    }

    public void delete(UUID playerId, String kitId) {
        String id = kitId.toLowerCase(java.util.Locale.ROOT);
        Map<String, PlayerKitData> player = cache.get(playerId);
        if (player != null) { player.remove(id); if (player.isEmpty()) cache.remove(playerId); }
        storage.deleteKit(playerId, id).whenComplete((ignored, failure) -> { if (failure != null) plugin.getLogger().warning("Could not delete custom kit " + id + " for " + playerId + ": " + failure.getMessage()); });
    }

    public boolean has(UUID playerId, String kitId) { return find(playerId, kitId).isPresent(); }
    public void clear(UUID playerId) { cache.remove(playerId); }
    private static String key(UUID playerId, String kitId) { return playerId + ":" + kitId; }
}
