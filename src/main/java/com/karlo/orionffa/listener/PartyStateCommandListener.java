package com.karlo.orionffa.listener;

import com.karlo.orionffa.gui.LobbyMenuManager;
import com.karlo.orionffa.gui.PartyHotbarManager;
import com.karlo.orionffa.party.Party;
import com.karlo.orionffa.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Keeps the temporary party hotbar synchronized with command-based party changes. */
public final class PartyStateCommandListener implements Listener {
    private final PartyManager parties;
    private final PartyHotbarManager hotbar;
    private final LobbyMenuManager lobby;

    public PartyStateCommandListener(PartyManager parties, PartyHotbarManager hotbar, LobbyMenuManager lobby) {
        this.parties = parties;
        this.hotbar = hotbar;
        this.lobby = lobby;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().trim().split("\\s+");
        if (args.length < 2) return;
        String root = args[0].toLowerCase(Locale.ROOT);
        if (!root.equals("/offa") && !root.equals("/orionffa")) return;
        if (!args[1].equalsIgnoreCase("party")) return;
        Player player = event.getPlayer();
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        List<UUID> restore = new ArrayList<>();
        Party before = parties.find(player.getUniqueId()).orElse(null);
        if ("disband".equals(action) && before != null && before.leader().equals(player.getUniqueId())) restore.addAll(before.members());
        UUID targetId = null;
        if (("kick".equals(action) || "leave".equals(action)) && args.length >= 4) {
            Player target = Bukkit.getPlayerExact(args[3]);
            if (target != null) targetId = target.getUniqueId();
        }
        UUID finalTargetId = targetId;
        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("OrionFFA-Core"), () -> {
            if ("create".equals(action) || "join".equals(action)) {
                if (parties.find(player.getUniqueId()).isPresent()) hotbar.apply(player);
            } else if ("leave".equals(action)) {
                if (finalTargetId != null) Bukkit.getPlayer(finalTargetId);
                if (parties.find(player.getUniqueId()).isEmpty()) lobby.apply(player); else hotbar.apply(player);
            } else if ("kick".equals(action) && finalTargetId != null && parties.find(finalTargetId).isEmpty()) {
                Player kicked = Bukkit.getPlayer(finalTargetId); if (kicked != null) lobby.apply(kicked);
            } else if ("disband".equals(action)) {
                for (UUID id : restore) { Player member = Bukkit.getPlayer(id); if (member != null) lobby.apply(member); }
            } else if (parties.find(player.getUniqueId()).isPresent()) {
                hotbar.apply(player);
            }
        });
    }
}
