package com.karlo.orionffa.listener;

import com.karlo.orionffa.gui.GuiManager;
import com.karlo.orionffa.gui.LobbyMenuManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class GuiProtectionListener implements Listener {
    private final GuiManager guis;
    private final LobbyMenuManager lobbyMenus;

    public GuiProtectionListener(GuiManager guis, LobbyMenuManager lobbyMenus) {
        this.guis = guis;
        this.lobbyMenus = lobbyMenus;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (guis.owns(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            guis.handle(player, event.getCurrentItem());
            return;
        }
        if (event.getWhoClicked() instanceof Player player && lobbyMenus.isLobbyItem(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (guis.owns(event.getView().getTopInventory())) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player
                && (lobbyMenus.isLobbyItem(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(lobbyMenus::isLobbyItem))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!lobbyMenus.isLobbyItem(item)) return;
        event.setCancelled(true);
        guis.handle(event.getPlayer(), item);
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (lobbyMenus.isLobbyItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && guis.owns(event.getInventory())) guis.closed(player, event.getInventory());
    }
}
