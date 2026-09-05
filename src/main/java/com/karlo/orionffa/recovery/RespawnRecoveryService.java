package com.karlo.orionffa.recovery;

import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.player.FfaState;
import com.karlo.orionffa.player.PlayerSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class RespawnRecoveryService {
    private final JavaPlugin plugin;
    private final PlayerSessionManager sessions;
    private final ArenaManager arenas;
    private final FfaService ffa;

    public RespawnRecoveryService(JavaPlugin plugin, PlayerSessionManager sessions, ArenaManager arenas, FfaService ffa) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.arenas = arenas;
        this.ffa = ffa;
    }

    public void recover(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        sessions.get(player.getUniqueId()).ifPresent(session -> {
            if (session.state() != FfaState.FFA || session.arenaId() == null) return;
            arenas.get(session.arenaId()).flatMap(arena -> arena.spawn().resolve()).ifPresent(event::setRespawnLocation);
            session.state(FfaState.RECOVERING);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) ffa.recover(player);
            });
        });
    }
}
