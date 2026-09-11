package com.karlo.orionffa.gui;

import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.party.PartyManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/** Owns the temporary party hotbar. It deliberately has no dependency on the existing GUI actions. */
public final class PartyHotbarManager {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final PartyManager parties;
    private final NamespacedKey modeKey;
    private final NamespacedKey actionKey;

    public PartyHotbarManager(JavaPlugin plugin, MessageService messages, PartyManager parties) {
        this.plugin = plugin;
        this.messages = messages;
        this.parties = parties;
        this.modeKey = new NamespacedKey(plugin, "hotbar_mode");
        this.actionKey = new NamespacedKey(plugin, "party_action");
    }

    public boolean isPartyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return "PARTY".equals(data.get(modeKey, PersistentDataType.STRING));
    }

    public String action(ItemStack item) {
        if (!isPartyItem(item)) return "";
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(actionKey, PersistentDataType.STRING, "");
    }

    public boolean isInParty(UUIDHolder holder) { return parties.find(holder.id()).isPresent(); }

    public void apply(Player player) {
        if (parties.find(player.getUniqueId()).isEmpty()) return;
        YamlConfiguration config = load();
        ConfigurationSection items = config.getConfigurationSection("menus.party_hotbar.items");
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHeldItemSlot(0);
        if (items == null) return;
        for (String id : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(id);
            if (section == null) continue;
            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot > 8) continue;
            player.getInventory().setItem(slot, item(section));
        }
    }

    public void clearAndRestoreLobby(Player player, LobbyMenuManager lobby) {
        lobby.apply(player);
    }

    public void createOrEnter(Player player, LobbyMenuManager lobby) {
        if (parties.find(player.getUniqueId()).isEmpty()) {
            var result = parties.create(player.getUniqueId());
            if (!result.success()) {
                player.sendMessage(messages.component("<red>" + result.reason() + "</red>"));
                return;
            }
            messages.send(player, "party-created");
        }
        apply(player);
    }

    private YamlConfiguration load() {
        File file = new File(plugin.getDataFolder(), "guis.yml");
        if (!file.exists()) plugin.saveResource("guis.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    private ItemStack item(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(section.getString("name", "<red>Invalid item")));
        meta.lore(section.getStringList("lore").stream().map(messages::component).toList());
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(modeKey, PersistentDataType.STRING, "PARTY");
        data.set(actionKey, PersistentDataType.STRING, section.getString("action", ""));
        item.setItemMeta(meta);
        return item;
    }

    /** Tiny value wrapper used only to keep the public API explicit. */
    public record UUIDHolder(java.util.UUID id) { }
}
