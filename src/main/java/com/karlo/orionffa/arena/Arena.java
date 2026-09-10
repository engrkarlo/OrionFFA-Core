package com.karlo.orionffa.arena;

import com.karlo.orionffa.config.LocationConfig;
import org.bukkit.Location;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Arena {
    private final String id;
    private final LocationConfig spawn;
    private final LocationConfig resetOrigin;
    private final ArenaSelection selection;
    private final boolean enabled;
    private final int capacity;
    private final String boundKit;
    private final Set<String> allowedKits;
    private final boolean shared;
    private final boolean locked;
    private final LocationConfig splitSpawnA;
    private final LocationConfig splitSpawnB;
    private final String schematicPath;
    private final Set<UUID> occupants = new HashSet<>();
    private final HashMap<UUID, Instant> reservations = new HashMap<>();

    public Arena(String id, LocationConfig spawn, LocationConfig resetOrigin, ArenaSelection selection,
                 boolean enabled, int capacity, String boundKit, Set<String> allowedKits, boolean shared,
                 boolean locked, LocationConfig splitSpawnA, LocationConfig splitSpawnB, String schematicPath) {
        this.id = id;
        this.spawn = spawn;
        this.resetOrigin = resetOrigin;
        this.selection = selection;
        this.enabled = enabled;
        this.capacity = capacity;
        this.boundKit = boundKit;
        this.allowedKits = Set.copyOf(allowedKits);
        this.shared = shared;
        this.locked = locked;
        this.splitSpawnA = splitSpawnA;
        this.splitSpawnB = splitSpawnB;
        this.schematicPath = schematicPath;
    }

    public String id() { return id; }
    public LocationConfig spawn() { return spawn; }
    public LocationConfig resetOrigin() { return resetOrigin; }
    public ArenaSelection selection() { return selection; }
    public String boundKit() { return boundKit; }
    public Set<String> allowedKits() { return allowedKits; }
    public boolean enabled() { return enabled; }
    public boolean locked() { return locked; }
    public LocationConfig splitSpawnA() { return splitSpawnA; }
    public LocationConfig splitSpawnB() { return splitSpawnB; }
    public String schematicPath() { return schematicPath; }
    public synchronized int occupants() { return occupants.size(); }
    public int capacity() { return capacity; }

    public boolean supports(String kitId) {
        return kitId.equals(boundKit) || allowedKits.contains(kitId) || shared;
    }

    public boolean contains(Location location) {
        return selection != null && selection.contains(location);
    }

    public synchronized boolean reserve(UUID playerId, Instant expiresAt) {
        cleanReservations(Instant.now());
        if (occupants.contains(playerId)) return true;
        if (occupants.size() + reservations.size() >= capacity) return false;
        reservations.put(playerId, expiresAt);
        return true;
    }

    public synchronized boolean claim(UUID playerId) {
        cleanReservations(Instant.now());
        if (!occupants.contains(playerId) && !reservations.containsKey(playerId)) return false;
        reservations.remove(playerId);
        occupants.add(playerId);
        return true;
    }

    public synchronized void release(UUID playerId) { reservations.remove(playerId); }

    public synchronized void leave(UUID playerId) {
        reservations.remove(playerId);
        occupants.remove(playerId);
    }

    private void cleanReservations(Instant now) {
        reservations.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
