package com.karlo.orionffa.listener;

import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.gui.GuiManager;
import com.karlo.orionffa.gui.KitGuiManager;
import com.karlo.orionffa.gui.LobbyMenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public final class GuiProtectionListener implements Listener {
    private final GuiManager guis;
    private final KitGuiManager kitGuis;
    private final LobbyMenuManager lobbyMenus;
    private final ArenaManager arenas;

    public GuiProtectionListener(GuiManager guis, KitGuiManager kitGuis, LobbyMenuManager lobbyMenus, ArenaManager arenas) {
        this.guis = guis; this.kitGuis = kitGuis; this.lobbyMenus = lobbyMenus; this.arenas = arenas;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (kitGuis.owns(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getRawSlot() < event.getView().getTopInventory().getSize()) kitGuis.handle(player, event.getCurrentItem());
            return;
        }
        if (guis.owns(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            guis.handle(player, event.getCurrentItem());
            return;
        }
        if (event.getWhoClicked() instanceof Player player && kitGuis.isEditorControl(event.getCurrentItem())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) kitGuis.handleEditorControl(player, event.getCurrentItem());
            return;
        }
        if (event.getWhoClicked() instanceof Player player && (lobbyMenus.isLobbyItem(event.getCurrentItem()) || arenas.isSelectionCancelItem(event.getCurrentItem()))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (kitGuis.owns(event.getView().getTopInventory())) { int top = event.getView().getTopInventory().getSize(); if (event.getRawSlots().stream().anyMatch(slot -> slot < top)) event.setCancelled(true); return; }
        if (guis.owns(event.getView().getTopInventory())) { int top = event.getView().getTopInventory().getSize(); if (event.getRawSlots().stream().anyMatch(slot -> slot < top)) event.setCancelled(true); return; }
        if (event.getWhoClicked() instanceof Player player && (kitGuis.isEditorControl(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(kitGuis::isEditorControl))) event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && (lobbyMenus.isLobbyItem(event.getOldCursor()) || arenas.isSelectionCancelItem(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(item -> lobbyMenus.isLobbyItem(item) || arenas.isSelectionCancelItem(item)))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void interact(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem(); Player player = event.getPlayer();
        if (kitGuis.isEditorControl(item)) { event.setCancelled(true); kitGuis.handleEditorControl(player, item); return; }
        if (arenas.isSelectionCancelItem(item)) { event.setCancelled(true); arenas.cancelSelection(player); return; }
        if (!lobbyMenus.isLobbyItem(item)) return;
        event.setCancelled(true);
        String lobbyAction = lobbyMenus.action(item);
        if ("open_kit_editor".equals(lobbyAction)) kitGuis.openEditor(player);
        else if ("open_kits".equals(lobbyAction)) kitGuis.openSelector(player);
        else guis.handle(player, item);
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (kitGuis.isEditorControl(event.getItemDrop().getItemStack()) || lobbyMenus.isLobbyItem(event.getItemDrop().getItemStack()) || arenas.isSelectionCancelItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler public void join(PlayerJoinEvent event) { arenas.cancelSelection(event.getPlayer()); }
    @EventHandler public void close(InventoryCloseEvent event) { /* Dedicated kit GUI owns its own lifecycle. */ }
    @EventHandler public void chat(AsyncPlayerChatEvent event) { kitGuis.chat(event); }
}
