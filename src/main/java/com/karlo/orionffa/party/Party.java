package com.karlo.orionffa.party;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Party {
    private final UUID id = UUID.randomUUID();
    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, Instant> invites = new ConcurrentHashMap<>();
    private boolean chatEnabled;
    private UUID activeMatch;

    public Party(UUID leader) {
        this.leader = leader;
        members.add(leader);
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public void leader(UUID leader) { this.leader = leader; }
    public LinkedHashSet<UUID> members() { return members; }
    public Map<UUID, Instant> invites() { return invites; }
    public boolean chatEnabled() { return chatEnabled; }
    public UUID activeMatch() { return activeMatch; }
    public void activeMatch(UUID activeMatch) { this.activeMatch = activeMatch; }
    public boolean toggleChat() { return chatEnabled = !chatEnabled; }
}
