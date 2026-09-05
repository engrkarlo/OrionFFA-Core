package com.karlo.orionffa.player;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.List;

public record PlayerSnapshot(
        Location location,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack offhand,
        GameMode gameMode,
        int level,
        float experience,
        int foodLevel,
        double health,
        List<PotionEffect> effects,
        boolean allowFlight,
        boolean flying,
        boolean gliding,
        boolean swimming,
        boolean sprinting,
        boolean sneaking,
        int heldItemSlot,
        Vector velocity
) {
    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player.getLocation().clone(), copy(player.getInventory().getContents()),
                copy(player.getInventory().getArmorContents()), clone(player.getInventory().getItemInOffHand()),
                player.getGameMode(), player.getLevel(), player.getExp(), player.getFoodLevel(), player.getHealth(),
                List.copyOf(player.getActivePotionEffects()), player.getAllowFlight(), player.isFlying(), player.isGliding(),
                player.isSwimming(), player.isSprinting(), player.isSneaking(), player.getInventory().getHeldItemSlot(), player.getVelocity().clone());
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = clone(source[i]);
        return copy;
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
