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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Protects the lobby hotbar and routes only the rewritten kit actions. */
public final class GuiProtectionListener implements Listener {
    private final GuiManager guis;
    private final KitGuiManager kitGuis;
    private final LobbyMenuManager lobbyMenus;
    private final ArenaManager arenas;

    public GuiProtectionListener(GuiManager guis, KitGuiManager kitGuis,
                                 LobbyMenuManager lobbyMenus, ArenaManager arenas) {
        this.guis = guis;
        this.kitGuis = kitGuis;
        this.lobbyMenus = lobbyMenus;
        this.arenas = arenas;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (kitGuis.owns(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                kitGuis.handle(player, event.getCurrentItem());
            }
            return;
        }
        if (guis.owns(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                guis.handle(player, event.getCurrentItem());
            }
            return;
        }
        if (event.getWhoClicked() instanceof Player
                && (lobbyMenus.isLobbyItem(event.getCurrentItem())
                || arenas.isSelectionCancelItem(event.getCurrentItem()))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (kitGuis.owns(event.getView().getTopInventory()) || guis.owns(event.getView().getTopInventory())) {
            int topSize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player
                && (lobbyMenus.isLobbyItem(event.getOldCursor())
                || arenas.isSelectionCancelItem(event.getOldCursor())
                || event.getNewItems().values().stream().anyMatch(item ->
                lobbyMenus.isLobbyItem(item) || arenas.isSelectionCancelItem(item)))) event.setCancelled(true);
    }

    /** The two kit actions are rewritten; legacy lobby actions use GuiManager's original methods. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (arenas.isSelectionCancelItem(item)) {
            event.setCancelled(true);
            arenas.cancelSelection(player);
            return;
        }
        if (!lobbyMenus.isLobbyItem(item)) return;

        String id = lobbyMenus.action(item);
        switch (id) {
            case "open_kits" -> { event.setCancelled(true); kitGuis.openSelector(player); }
            case "open_kit_editor" -> { event.setCancelled(true); kitGuis.openEditor(player); }
            case "open_arenas" -> { event.setCancelled(true); guis.openArenas(player, null); }
            case "open_spectator" -> { event.setCancelled(true); guis.openSpectators(player); }
            case "open_party" -> { event.setCancelled(true); guis.openParty(player); }
            case "open_stats" -> { event.setCancelled(true); guis.openStatistics(player); }
            case "leave_ffa" -> event.setCancelled(true);
            default -> { /* Leave unknown legacy actions untouched for compatibility. */ }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (lobbyMenus.isLobbyItem(event.getItemDrop().getItemStack())
                || arenas.isSelectionCancelItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) { arenas.cancelSelection(event.getPlayer()); }

    @EventHandler
    public void chat(AsyncPlayerChatEvent event) { kitGuis.chat(event); }
}
