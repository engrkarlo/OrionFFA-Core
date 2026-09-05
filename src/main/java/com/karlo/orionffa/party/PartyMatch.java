package com.karlo.orionffa.party;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyMatch {
    private final UUID id = UUID.randomUUID();
    private final UUID partyId;
    private final String arenaId;
    private final Map<String, Set<UUID>> teams;
    private final Set<UUID> participants;
    private final Instant startTime;
    private volatile Instant endTime;
    private volatile PartyMatchState state = PartyMatchState.WAITING;

    public PartyMatch(UUID partyId, String arenaId, Map<String, Set<UUID>> teams, Set<UUID> participants) {
        this.partyId = partyId;
        this.arenaId = arenaId;
        this.teams = Map.copyOf(teams);
        this.participants = Set.copyOf(participants);
        this.startTime = Instant.now();
    }

    public UUID id() { return id; }
    public UUID partyId() { return partyId; }
    public String arenaId() { return arenaId; }
    public Map<String, Set<UUID>> teams() { return teams; }
    public Set<UUID> participants() { return participants; }
    public Instant startTime() { return startTime; }
    public Instant endTime() { return endTime; }
    public PartyMatchState state() { return state; }
    public void state(PartyMatchState state) {
        this.state = state;
        if (state == PartyMatchState.FINISHED || state == PartyMatchState.CLEANUP) endTime = Instant.now();
    }

    public boolean contains(UUID playerId) { return participants.contains(playerId); }

    public String teamOf(UUID playerId) {
        return teams.entrySet().stream().filter(e -> e.getValue().contains(playerId)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public boolean friendly(UUID a, UUID b) {
        String left = teamOf(a), right = teamOf(b);
        return left != null && left.equals(right);
    }

    public long aliveCount(List<UUID> online) {
        return online.stream().filter(participants::contains).filter(id -> {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
            return p != null && !p.isDead();
        }).count();
    }
}
