package com.karlo.orionffa.listener;

import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.ffa.ServiceResult;
import com.karlo.orionffa.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/**
 * Keeps /offa lobby usable as a return-to-lobby command from any world.
 * The normal /orionffa command gate intentionally restricts gameplay/admin
 * commands to the configured lobby world, so this narrowly intercepts only
 * the lobby return command before that world gate is evaluated.
 */
public final class LobbyCommandListener implements Listener {
    private final FfaService ffa;
    private final MessageService messages;

    public LobbyCommandListener(FfaService ffa, MessageService messages) {
        this.ffa = ffa;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().trim();
        if (command.startsWith("/")) command = command.substring(1).trim();
        String[] parts = command.split("\\s+");
        if (parts.length != 2) return;

        String root = parts[0].toLowerCase(Locale.ROOT);
        String subcommand = parts[1].toLowerCase(Locale.ROOT);
        if (!(root.equals("offa") || root.equals("orionffa")) || !subcommand.equals("lobby")) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("orionffa.use") && !player.hasPermission("orionffa.admin")) {
            messages.send(player, "no-permission");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        ServiceResult result = ffa.enterLobby(player);
        messages.send(player, result.messageKey(), result.placeholders());
    }
}
