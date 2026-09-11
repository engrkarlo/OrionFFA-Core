package com.karlo.orionffa.gui;

import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.party.Party;
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

public final class PartyHotbarManager {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final PartyManager parties;
    @SuppressWarnings("unused") private final ConfigManager config;
    private final NamespacedKey modeKey;
    private final NamespacedKey actionKey;

    public PartyHotbarManager(JavaPlugin plugin, MessageService messages, PartyManager parties, ConfigManager config) {
        this.plugin = plugin; this.messages = messages; this.parties = parties; this.config = config;
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

    public void apply(Player player) {
        Party party = parties.find(player.getUniqueId()).orElse(null);
        if (party == null) return;
        YamlConfiguration file = loadAndMigrate();
        ConfigurationSection items = file.getConfigurationSection("menus.party_hotbar.items");
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHeldItemSlot(0);
        if (items == null) return;
        boolean leader = party.leader().equals(player.getUniqueId());
        for (String id : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            String action = section.getString("action", "");
            int slot = section.getInt("slot", -1);
            if (slot < 0 || slot > 8) continue;
            if ("disband".equalsIgnoreCase(action) && !leader) continue;
            player.getInventory().setItem(slot, item(section));
        }
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

    private YamlConfiguration loadAndMigrate() {
        File file = new File(plugin.getDataFolder(), "guis.yml");
        if (!file.exists()) plugin.saveResource("guis.yml", false);
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection party = current.getConfigurationSection("menus.party_hotbar.items");
        if (party != null && party.getConfigurationSection("invite") == null) {
            ConfigurationSection invite = party.createSection("invite");
            invite.set("slot", 1);
            invite.set("material", "WRITABLE_BOOK");
            invite.set("name", "<green>Invite Players");
            invite.set("lore", java.util.List.of("<gray>Invite players currently in the FFA lobby."));
            invite.set("action", "invite");
            try { current.save(file); } catch (java.io.IOException exception) { plugin.getLogger().warning("Could not migrate party invite hotbar item: " + exception.getMessage()); }
        }
        return current;
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
}
