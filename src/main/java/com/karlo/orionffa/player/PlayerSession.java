package com.karlo.orionffa.player;

import java.util.UUID;

public final class PlayerSession {
    private final UUID playerId;
    private final PlayerSnapshot snapshot;
    private FfaState state = FfaState.OUTSIDE;
    private String kitId;
    private String arenaId;
    private UUID spectatorTarget;

    public PlayerSession(UUID playerId, PlayerSnapshot snapshot) {
        this.playerId = playerId;
        this.snapshot = snapshot;
    }

    public UUID playerId() { return playerId; }
    public PlayerSnapshot snapshot() { return snapshot; }
    public FfaState state() { return state; }
    public void state(FfaState state) { this.state = state; }
    public String kitId() { return kitId; }
    public void kitId(String kitId) { this.kitId = kitId; }
    public String arenaId() { return arenaId; }
    public void arenaId(String arenaId) { this.arenaId = arenaId; }
    public UUID spectatorTarget() { return spectatorTarget; }
    public void spectatorTarget(UUID spectatorTarget) { this.spectatorTarget = spectatorTarget; }
}
