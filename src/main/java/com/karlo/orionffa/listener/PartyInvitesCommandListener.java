package com.karlo.orionffa.listener;

import com.karlo.orionffa.gui.PartyInviteGuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Provides the short /offa invites command without changing the existing command router. */
public final class PartyInvitesCommandListener implements Listener {
    private final PartyInviteGuiManager invites;

    public PartyInvitesCommandListener(PartyInviteGuiManager invites) { this.invites = invites; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.equals("/offa invites") && !lower.equals("/orionffa invites")) return;
        event.setCancelled(true);
        invites.openInvitations(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void tab(TabCompleteEvent event) {
        String buffer = event.getBuffer().toLowerCase(Locale.ROOT);
        if (buffer.equals("/offa") || buffer.equals("/orionffa") || buffer.startsWith("/offa ") || buffer.startsWith("/orionffa ")) {
            String trimmed = buffer.trim();
            if (trimmed.equals("/offa") || trimmed.equals("/orionffa")) return;
            String prefix = buffer.startsWith("/offa") ? "/offa " : "/orionffa ";
            String argument = buffer.substring(prefix.length());
            if (!argument.contains(" ") && "invites".startsWith(argument)) {
                List<String> completions = new ArrayList<>(event.getCompletions());
                if (!completions.contains("invites")) completions.add("invites");
                event.setCompletions(completions);
            }
        }
    }
}
