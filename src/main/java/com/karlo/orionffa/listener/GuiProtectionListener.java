package com.karlo.orionffa.listener;

import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.gui.GuiManager;
import com.karlo.orionffa.gui.KitGuiManager;
import com.karlo.orionffa.gui.LobbyMenuManager;
import com.karlo.orionffa.gui.PartyHotbarManager;
import com.karlo.orionffa.gui.PartySplitGuiManager;
import com.karlo.orionffa.gui.SpectatorGuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import java.util.Locale;

public final class GuiProtectionListener implements Listener {
    private final GuiManager guis;
    private final KitGuiManager kitGuis;
    private final SpectatorGuiManager spectators;
    private final LobbyMenuManager lobbyMenus;
    private final ArenaManager arenas;
    private final PartyHotbarManager partyHotbar;
    private final PartySplitGuiManager partySplit;

    public GuiProtectionListener(GuiManager guis, KitGuiManager kitGuis, SpectatorGuiManager spectators,
                                 LobbyMenuManager lobbyMenus, ArenaManager arenas,
                                 PartyHotbarManager partyHotbar, PartySplitGuiManager partySplit) {
        this.guis=guis; this.kitGuis=kitGuis; this.spectators=spectators; this.lobbyMenus=lobbyMenus; this.arenas=arenas;
        this.partyHotbar=partyHotbar; this.partySplit=partySplit;
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        if (partySplit.owns(event.getView().getTopInventory())) { partySplit.handle(event); return; }
        if (spectators.owns(event.getView().getTopInventory())) { event.setCancelled(true); if(event.getWhoClicked() instanceof Player p && event.getRawSlot()<event.getView().getTopInventory().getSize()) spectators.handle(p,event.getCurrentItem()); return; }
        if (kitGuis.owns(event.getView().getTopInventory())) { event.setCancelled(true); if(event.getWhoClicked() instanceof Player p && event.getRawSlot()<event.getView().getTopInventory().getSize()) kitGuis.handle(p,event.getCurrentItem()); return; }
        if (guis.owns(event.getView().getTopInventory())) { event.setCancelled(true); if(event.getWhoClicked() instanceof Player p && event.getRawSlot()<event.getView().getTopInventory().getSize()) guis.handle(p,event.getCurrentItem()); return; }
        if(event.getWhoClicked() instanceof Player && (lobbyMenus.isLobbyItem(event.getCurrentItem())||arenas.isSelectionCancelItem(event.getCurrentItem())||partyHotbar.isPartyItem(event.getCurrentItem()))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled=true)
    public void drag(InventoryDragEvent event) {
        if(partySplit.owns(event.getView().getTopInventory())||spectators.owns(event.getView().getTopInventory())||kitGuis.owns(event.getView().getTopInventory())||guis.owns(event.getView().getTopInventory())) { int top=event.getView().getTopInventory().getSize(); if(event.getRawSlots().stream().anyMatch(s->s<top)) event.setCancelled(true); return; }
        if(event.getWhoClicked() instanceof Player && (lobbyMenus.isLobbyItem(event.getOldCursor())||arenas.isSelectionCancelItem(event.getOldCursor())||partyHotbar.isPartyItem(event.getOldCursor())||event.getNewItems().values().stream().anyMatch(i->lobbyMenus.isLobbyItem(i)||arenas.isSelectionCancelItem(i)||partyHotbar.isPartyItem(i)))) event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interact(PlayerInteractEvent event) {
        Action action=event.getAction(); if(action!=Action.LEFT_CLICK_AIR&&action!=Action.RIGHT_CLICK_AIR&&action!=Action.LEFT_CLICK_BLOCK&&action!=Action.RIGHT_CLICK_BLOCK)return;
        Player player=event.getPlayer(); ItemStack item=event.getItem();
        if(arenas.isSelectionCancelItem(item)){event.setCancelled(true);arenas.cancelSelection(player);return;}
        if(partyHotbar.isPartyItem(item)) return;
        if(!lobbyMenus.isLobbyItem(item))return;
        event.setCancelled(true);
        switch(lobbyMenus.action(item)){
            case "open_kits" -> kitGuis.openSelector(player);
            case "open_kit_editor" -> kitGuis.openEditor(player);
            case "open_arenas" -> guis.openArenas(player,null);
            case "open_spectator" -> spectators.open(player);
            case "open_party" -> partyHotbar.createOrEnter(player, lobbyMenus);
            case "open_stats" -> guis.openStatistics(player);
            default -> { }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void command(PlayerCommandPreprocessEvent event) {
        Player player=event.getPlayer();
        if(!spectators.isSpectating(player))return;
        String command=event.getMessage().trim().toLowerCase(Locale.ROOT);
        if(command.equals("/offa lobby")||command.equals("/orionffa lobby")||command.equals("/offa spectate leave")||command.equals("/orionffa spectate leave")){
            event.setCancelled(true); spectators.exit(player);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void drop(PlayerDropItemEvent event){if(lobbyMenus.isLobbyItem(event.getItemDrop().getItemStack())||arenas.isSelectionCancelItem(event.getItemDrop().getItemStack())||partyHotbar.isPartyItem(event.getItemDrop().getItemStack()))event.setCancelled(true);}
    @EventHandler public void join(PlayerJoinEvent event){arenas.cancelSelection(event.getPlayer());}
    @EventHandler public void chat(AsyncPlayerChatEvent event){kitGuis.chat(event);}
}
