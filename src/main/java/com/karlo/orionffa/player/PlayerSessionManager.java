package com.karlo.orionffa.player;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSessionManager {
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public PlayerSession enter(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerSession(player.getUniqueId(), PlayerSnapshot.capture(player)));
    }

    public Optional<PlayerSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public boolean active(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public Collection<PlayerSession> active() {
        return sessions.values();
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clear() {
        sessions.clear();
    }
}
