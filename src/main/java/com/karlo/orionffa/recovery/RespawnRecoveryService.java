package com.karlo.orionffa.recovery;

import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.config.ConfigManager;
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
            if (arenas.get(session.arenaId()).isEmpty()) return;

            // An FFA arena death ends the arena session. The player is sent to the configured
            // lobby after Bukkit completes the respawn event, preventing vanilla spawn from winning.
            event.setRespawnLocation(resolveLobby(player));
            session.state(FfaState.RECOVERING);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) ffa.leaveToLobby(player);
            });
        });
    }

    private org.bukkit.Location resolveLobby(Player player) {
        ConfigManager config = new ConfigManager(plugin);
        return config.runtime().lobby().resolve().orElse(player.getWorld().getSpawnLocation());
    }
}
