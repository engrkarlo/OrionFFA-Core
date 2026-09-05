package com.karlo.orionffa.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class GuiHolder implements InventoryHolder {
    private final GuiType type;
    private Inventory inventory;

    public GuiHolder(GuiType type) {
        this.type = type;
    }

    public GuiType type() { return type; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("GUI inventory is not initialized");
        return inventory;
    }
}
