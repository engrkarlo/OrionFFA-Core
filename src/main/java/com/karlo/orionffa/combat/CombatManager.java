package com.karlo.orionffa.combat;

import com.karlo.orionffa.player.FfaState;
import com.karlo.orionffa.player.PlayerSession;
import com.karlo.orionffa.player.PlayerSessionManager;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CombatManager {
    private final PlayerSessionManager sessions;
    private final Duration creditDuration;
    private final Map<UUID, Attack> lastAttacker = new HashMap<>();

    public CombatManager(PlayerSessionManager sessions, Duration creditDuration) {
        this.sessions = sessions;
        this.creditDuration = creditDuration;
    }

    public void recordDamage(Player attacker, Player victim) {
        Optional<PlayerSession> attackerSession = sessions.get(attacker.getUniqueId());
        Optional<PlayerSession> victimSession = sessions.get(victim.getUniqueId());
        if (attackerSession.isEmpty() || victimSession.isEmpty()) return;
        if (attackerSession.get().state() != FfaState.FFA || victimSession.get().state() != FfaState.FFA) return;
        if (attackerSession.get().arenaId() == null || !attackerSession.get().arenaId().equals(victimSession.get().arenaId())) return;
        lastAttacker.put(victim.getUniqueId(), new Attack(attacker.getUniqueId(), Instant.now().plus(creditDuration)));
    }

    public Optional<UUID> killerFor(Player victim) {
        if (victim.getKiller() != null) return Optional.of(victim.getKiller().getUniqueId());
        Attack attack = lastAttacker.get(victim.getUniqueId());
        return attack != null && attack.expiresAt().isAfter(Instant.now()) ? Optional.of(attack.attacker()) : Optional.empty();
    }

    public void clear(UUID playerId) {
        lastAttacker.remove(playerId);
        lastAttacker.entrySet().removeIf(entry -> entry.getValue().attacker().equals(playerId));
    }

    public void cleanup() {
        Instant now = Instant.now();
        lastAttacker.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    public int activeCount() { return lastAttacker.size(); }

    private record Attack(UUID attacker, Instant expiresAt) { }
}
