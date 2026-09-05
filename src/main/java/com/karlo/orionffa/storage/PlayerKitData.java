package com.karlo.orionffa.storage;

import org.bukkit.inventory.ItemStack;

public record PlayerKitData(ItemStack[] inventory, ItemStack[] armor, ItemStack offhand) {
    public PlayerKitData {
        inventory = copy(inventory);
        armor = copy(armor);
        offhand = offhand == null ? null : offhand.clone();
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = source == null ? new ItemStack[0] : new ItemStack[source.length];
        for (int i = 0; i < result.length; i++) result[i] = source[i] == null ? null : source[i].clone();
        return result;
    }
}
