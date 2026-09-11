package com.karlo.orionffa.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Dedicated holder so the built-in kit GUI can coexist with the general GUI manager. */
public final class KitGuiHolder implements InventoryHolder {
    private final String menu;
    private Inventory inventory;
    public KitGuiHolder(String menu) { this.menu = menu; }
    public String menu() { return menu; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
