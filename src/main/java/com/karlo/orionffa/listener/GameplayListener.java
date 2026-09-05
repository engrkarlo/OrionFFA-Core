package com.karlo.orionffa.listener;

import com.karlo.orionffa.combat.CombatManager;
import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.party.PartyManager;
import com.karlo.orionffa.party.PartyMatchService;
import com.karlo.orionffa.kit.KitManager;
import com.karlo.orionffa.kit.KitPersistenceManager;
import com.karlo.orionffa.player.PlayerSessionManager;
import com.karlo.orionffa.recovery.RespawnRecoveryService;
import com.karlo.orionffa.statistics.StatisticsManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class GameplayListener implements Listener {
    private final PlayerSessionManager sessions;
    private final FfaService ffa;
    private final CombatManager combat;
    private final StatisticsManager statistics;
    private final RespawnRecoveryService recovery;
    private final PartyManager parties;
    private final PartyMatchService matches;
    private final KitPersistenceManager customKits;
    private final KitManager kits;

    public GameplayListener(PlayerSessionManager sessions, FfaService ffa, CombatManager combat, StatisticsManager statistics,
                            RespawnRecoveryService recovery, PartyManager parties, PartyMatchService matches, KitPersistenceManager customKits, KitManager kits) {
        this.sessions = sessions;
        this.ffa = ffa;
        this.combat = combat;
        this.statistics = statistics;
        this.recovery = recovery;
        this.parties = parties;
        this.matches = matches;
        this.customKits = customKits;
        this.kits = kits;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        statistics.load(event.getPlayer().getUniqueId());
        customKits.load(event.getPlayer().getUniqueId(), kits.available());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ffa.targetLeft(player.getUniqueId());
        ffa.leave(player);
        ffa.cleanup(player);
        combat.clear(player.getUniqueId());
        matches.disconnect(player.getUniqueId());
        parties.remove(player.getUniqueId());
        statistics.unload(player.getUniqueId());
        customKits.clear(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event);
        if (attacker != null) {
            if (matches.shouldCancelDamage(attacker, victim)) event.setCancelled(true);
            else combat.recordDamage(attacker, victim);
        }
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (matches.handleDeath(victim)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            statistics.recordDeath(victim.getUniqueId());
            return;
        }
        if (!sessions.active(victim.getUniqueId())) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        statistics.recordDeath(victim.getUniqueId());
        combat.killerFor(victim).ifPresent(statistics::recordKill);
        combat.clear(victim.getUniqueId());
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        if (!matches.recover(event.getPlayer())) recovery.recover(event);
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player player ? player : null;
        }
        return null;
    }
}
