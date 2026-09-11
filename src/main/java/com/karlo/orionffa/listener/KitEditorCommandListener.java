package com.karlo.orionffa.listener;

import com.karlo.orionffa.gui.KitGuiManager;
import com.karlo.orionffa.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/** Keeps kit-editor controls command based so the physical kit inventory stays completely free. */
public final class KitEditorCommandListener implements Listener {
    private final KitGuiManager kits;
    private final MessageService messages;

    public KitEditorCommandListener(KitGuiManager kits, MessageService messages) { this.kits = kits; this.messages = messages; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!kits.isEditing(player)) return;
        String raw = event.getMessage().trim();
        String[] parts = raw.substring(1).split("\\s+");
        if (parts.length < 2) return;
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (!(root.equals("offa") || root.equals("orionffa"))) return;
        if (!parts[1].equalsIgnoreCase("kit")) return;
        if (parts.length < 3) {
            event.setCancelled(true);
            messages.send(player, "kit-editor-usage");
            return;
        }
        String action = parts[2].toLowerCase(Locale.ROOT);
        if (action.equals("save")) {
            event.setCancelled(true);
            send(player, kits.save(player));
        } else if (action.equals("delete")) {
            event.setCancelled(true);
            send(player, kits.delete(player));
        } else if (action.equals("leave")) {
            event.setCancelled(true);
            send(player, kits.leave(player));
        }
    }

    private void send(Player player, com.karlo.orionffa.ffa.ServiceResult result) {
        messages.send(player, result.messageKey(), result.placeholders());
    }
}
