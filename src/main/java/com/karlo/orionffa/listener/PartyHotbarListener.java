package com.karlo.orionffa.listener;

import com.karlo.orionffa.gui.LobbyMenuManager;
import com.karlo.orionffa.gui.PartyHotbarManager;
import com.karlo.orionffa.gui.PartySplitGuiManager;
import com.karlo.orionffa.party.Party;
import com.karlo.orionffa.party.PartyManager;
import com.karlo.orionffa.party.PartyMatchService;
import com.karlo.orionffa.party.PartyResult;
import com.karlo.orionffa.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/** Handles only the party hotbar. Existing lobby/arena/kit/spectator listeners remain independent. */
public final class PartyHotbarListener implements Listener {
    private final PartyHotbarManager hotbar;
    private final PartyManager parties;
    private final PartyMatchService matches;
    private final PartySplitGuiManager splitGui;
    private final LobbyMenuManager lobby;
    private final MessageService messages;

    public PartyHotbarListener(PartyHotbarManager hotbar, PartyManager parties, PartyMatchService matches,
                               PartySplitGuiManager splitGui, LobbyMenuManager lobby, MessageService messages) {
        this.hotbar = hotbar;
        this.parties = parties;
        this.matches = matches;
        this.splitGui = splitGui;
        this.lobby = lobby;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void interact(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!hotbar.isPartyItem(item)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (parties.find(player.getUniqueId()).isEmpty()) { lobby.apply(player); return; }
        switch (hotbar.action(item)) {
            case "members" -> members(player);
            case "split" -> splitGui.open(player);
            case "chat" -> {
                boolean enabled = parties.toggleChatState(player.getUniqueId());
                player.sendMessage(messages.component(enabled ? "<green>Party messages enabled." : "<yellow>Party messages disabled."));
            }
            case "leave" -> leave(player);
            case "disband" -> disband(player);
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void click(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && hotbar.isPartyItem(event.getCurrentItem())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player && (hotbar.isPartyItem(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(hotbar::isPartyItem))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false)
    public void drop(PlayerDropItemEvent event) {
        if (hotbar.isPartyItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        matches.find(player.getUniqueId()).ifPresent(match -> matches.disconnect(player.getUniqueId()));
        parties.remove(player.getUniqueId());
    }

    private void members(Player player) {
        Party party = parties.find(player.getUniqueId()).orElse(null);
        if (party == null) return;
        player.sendMessage(messages.component("<gold>Party Members <gray>(" + party.members().size() + ")</gray>"));
        for (java.util.UUID id : party.members()) {
            Player member = Bukkit.getPlayer(id);
            String name = member == null ? id.toString() : member.getName();
            String role = id.equals(party.leader()) ? " <yellow>(Leader)" : "";
            player.sendMessage(messages.component("<gray>• <white>" + name + role));
        }
    }

    private void leave(Player player) {
        Party before = parties.find(player.getUniqueId()).orElse(null);
        java.util.UUID promoted = before != null && before.leader().equals(player.getUniqueId()) && before.members().size() > 1
                ? before.members().stream().filter(id -> !id.equals(player.getUniqueId())).findFirst().orElse(null) : null;
        PartyResult result = parties.leave(player.getUniqueId());
        if (!result.success()) { player.sendMessage(messages.component("<red>" + result.reason() + "</red>")); return; }
        messages.send(player, "party-left");
        lobby.apply(player);
        if (promoted != null) Bukkit.getPlayer(promoted).ifPresent(hotbar::apply);
    }

    private void disband(Player player) {
        Party party = parties.find(player.getUniqueId()).orElse(null);
        if (party == null || !party.leader().equals(player.getUniqueId())) {
            player.sendMessage(messages.component("<red>Only the party leader can disband the party.</red>"));
            return;
        }
        java.util.List<Player> members = party.members().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).toList();
        PartyResult result = parties.disband(player.getUniqueId());
        if (!result.success()) { player.sendMessage(messages.component("<red>" + result.reason() + "</red>")); return; }
        for (Player member : members) lobby.apply(member);
        messages.send(player, "party-disbanded");
    }
}
