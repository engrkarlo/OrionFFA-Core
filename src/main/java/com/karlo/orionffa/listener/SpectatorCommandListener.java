package com.karlo.orionffa.listener;

import com.karlo.orionffa.gui.SpectatorGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Ensures command-based spectating receives the same dedicated hotbar as GUI spectating. */
public final class SpectatorCommandListener implements Listener {
    private final JavaPlugin plugin;
    private final SpectatorGuiManager spectators;

    public SpectatorCommandListener(JavaPlugin plugin, SpectatorGuiManager spectators) {
        this.plugin = plugin;
        this.spectators = spectators;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!command.equals("/offa spectate") && !command.startsWith("/offa spectate ")
                && !command.equals("/orionffa spectate") && !command.startsWith("/orionffa spectate ")) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (spectators.isSpectating(player)) spectators.applyHotbar(player);
        });
    }
}
