package com.karlo.orionffa.gui;

import com.karlo.orionffa.message.MessageService;
import net.kyori.adventure.text.Component;
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

/** Builds the configurable lobby hotbar and tags its items for GUI actions. */
public final class LobbyMenuManager {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final NamespacedKey guiKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;

    public LobbyMenuManager(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        guiKey = new NamespacedKey(plugin, "gui");
        actionKey = new NamespacedKey(plugin, "action");
        targetKey = new NamespacedKey(plugin, "target");
    }

    public void apply(Player player) {
        File file = new File(plugin.getDataFolder(), "guis.yml");
        if (!file.exists()) plugin.saveResource("guis.yml", false);
        YamlConfiguration definitions = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(java.util.Objects.requireNonNull(plugin.getResource("guis.yml"), "guis.yml resource missing"), java.nio.charset.StandardCharsets.UTF_8));
        if (definitions.getConfigurationSection("menus.lobby.items") == null) { copySection(defaults, definitions, "menus.lobby.items"); changed = true; }
        if (definitions.getConfigurationSection("menus.main") != null) { definitions.set("menus.main", null); changed = true; }
        if (changed) {
            try { definitions.save(file); } catch (java.io.IOException exception) { plugin.getLogger().warning("Could not migrate guis.yml: " + exception.getMessage()); }
        }
        ConfigurationSection items = definitions.getConfigurationSection("menus.lobby.items");
        if (items == null) return;
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setHeldItemSlot(0);
        for (String id : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(id);
            if (item == null) continue;
            int slot = item.getInt("slot", -1);
            if (slot < 0 || slot > 8) continue;
            player.getInventory().setItem(slot, configuredItem(item));
        }
    }

    public boolean isLobbyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return "LOBBY".equals(data.get(guiKey, PersistentDataType.STRING)) && data.get(actionKey, PersistentDataType.STRING) != null;
    }

    public String action(ItemStack item) {
        if (!isLobbyItem(item)) return "";
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(actionKey, PersistentDataType.STRING, "");
    }

    public String target(ItemStack item) {
        if (!isLobbyItem(item)) return "";
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(targetKey, PersistentDataType.STRING, "");
    }

    private static void copySection(YamlConfiguration source, YamlConfiguration target, String path) {
        ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String child = path + "." + key;
            ConfigurationSection nested = source.getConfigurationSection(child);
            if (nested != null) copySection(source, target, child); else target.set(child, source.get(child));
        }
    }

    private ItemStack configuredItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(section.getString("name", "<red>Invalid item")));
        List<Component> lore = section.getStringList("lore").stream().map(messages::component).toList();
        meta.lore(lore);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(guiKey, PersistentDataType.STRING, "LOBBY");
        data.set(actionKey, PersistentDataType.STRING, section.getString("action", ""));
        data.set(targetKey, PersistentDataType.STRING, section.getString("target", ""));
        item.setItemMeta(meta);
        return item;
    }
}
