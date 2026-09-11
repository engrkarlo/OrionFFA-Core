package com.karlo.orionffa.listener;

import com.karlo.orionffa.gui.PartyInviteGuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class PartyInviteGuiListener implements Listener {
    private final PartyInviteGuiManager invites;

    public PartyInviteGuiListener(PartyInviteGuiManager invites) {
        this.invites = invites;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!invites.owns(event.getView().getTopInventory())) return;
        invites.handle(event);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (invites.owns(event.getView().getTopInventory())) event.setCancelled(true);
    }
}
