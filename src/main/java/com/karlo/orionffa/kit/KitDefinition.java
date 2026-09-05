package com.karlo.orionffa.kit;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public record KitDefinition(
        String id,
        String displayName,
        Material icon,
        String defaultArena,
        Map<Integer, ItemStack> inventory,
        ItemStack[] armor,
        ItemStack offhand,
        GameMode gameMode,
        boolean enabled
) {
}
