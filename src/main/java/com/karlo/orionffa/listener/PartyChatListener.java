package com.karlo.orionffa.listener;

import com.karlo.orionffa.party.PartyManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PartyChatListener implements Listener {
    private final JavaPlugin plugin;
    private final PartyManager parties;

    public PartyChatListener(JavaPlugin plugin, PartyManager parties) {
        this.plugin = plugin;
        this.parties = parties;
    }

    @EventHandler(ignoreCancelled = true)
    public void chat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!parties.partyChatEnabled(sender.getUniqueId())) return;
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component formatted = Component.text("[Party] " + sender.getName() + ": " + message);
            parties.recipients(sender.getUniqueId()).stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull)
                    .forEach(member -> member.sendMessage(formatted));
        });
    }
}
