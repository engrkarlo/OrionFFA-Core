package com.karlo.orionffa.kit;

import com.karlo.orionffa.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Owns the standalone admin-kit definitions stored in kits.yml. */
public final class KitManager {
    private final JavaPlugin plugin;
    @SuppressWarnings("unused") private final ConfigManager config;
    private final File file;
    private Map<String, KitDefinition> kits = Map.of();

    public KitManager(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("kits.yml", false);
        }
        YamlConfiguration root = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = root.getConfigurationSection("kits");
        if (section == null) {
            kits = Map.of();
            return;
        }
        Map<String, KitDefinition> loaded = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection kit = section.getConfigurationSection(id);
            if (kit == null) continue;
            loaded.put(id.toLowerCase(Locale.ROOT), load(id.toLowerCase(Locale.ROOT), kit));
        }
        kits = Map.copyOf(loaded);
    }

    public Optional<KitDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT))).filter(KitDefinition::enabled);
    }

    public boolean existsAndEnabled(String id) { return find(id).isPresent(); }
    public List<KitDefinition> available() { return kits.values().stream().filter(KitDefinition::enabled).sorted(Comparator.comparing(KitDefinition::id)).toList(); }
    public List<String> names() { return available().stream().map(KitDefinition::id).toList(); }

    /** Saves the admin's current inventory as a new public kit. */
    public synchronized boolean saveFromPlayer(Player player, String id) {
        String normalized = normalize(id);
        if (normalized.isBlank() || !normalized.matches("[a-z0-9_-]{1,32}")) return false;
        if (kits.containsKey(normalized)) return false;

        PlayerInventory inv = player.getInventory();
        Material icon = firstMaterial(inv.getContents());
        if (icon == null) icon = Material.CHEST;

        YamlConfiguration root = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection kitsSection = root.getConfigurationSection("kits");
        if (kitsSection == null) kitsSection = root.createSection("kits");
        ConfigurationSection section = kitsSection.createSection(normalized);
        section.set("enabled", true);
        section.set("display-name", "<aqua>" + normalized + "</aqua>");
        section.set("icon", icon.name());
        section.set("game-mode", player.getGameMode().name());
        section.set("inventory", null);
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) section.set("inventory." + slot, item.clone());
        }
        section.set("armor.boots", cloneOrNull(inv.getBoots()));
        section.set("armor.leggings", cloneOrNull(inv.getLeggings()));
        section.set("armor.chestplate", cloneOrNull(inv.getChestplate()));
        section.set("armor.helmet", cloneOrNull(inv.getHelmet()));
        section.set("offhand", cloneOrNull(inv.getItemInOffHand()));
        try {
            root.save(file);
            reload();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save admin kit " + normalized + ": " + exception.getMessage());
            return false;
        }
    }

    /** Replaces an existing admin kit with the admin's current inventory. */
    public synchronized boolean overwriteFromPlayer(Player player, String id) {
        String normalized = normalize(id);
        if (!kits.containsKey(normalized)) return false;
        YamlConfiguration root = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = root.getConfigurationSection("kits." + normalized);
        if (section == null) return false;
        Material icon = firstMaterial(player.getInventory().getContents());
        if (icon == null) icon = kits.get(normalized).icon();
        section.set("icon", icon.name());
        section.set("game-mode", player.getGameMode().name());
        section.set("inventory", null);
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) section.set("inventory." + slot, item.clone());
        }
        section.set("armor.boots", cloneOrNull(player.getInventory().getBoots()));
        section.set("armor.leggings", cloneOrNull(player.getInventory().getLeggings()));
        section.set("armor.chestplate", cloneOrNull(player.getInventory().getChestplate()));
        section.set("armor.helmet", cloneOrNull(player.getInventory().getHelmet()));
        section.set("offhand", cloneOrNull(player.getInventory().getItemInOffHand()));
        try { root.save(file); reload(); return true; }
        catch (IOException exception) { plugin.getLogger().warning("Could not update admin kit " + normalized + ": " + exception.getMessage()); return false; }
    }

    public synchronized boolean delete(String id) {
        String normalized = normalize(id);
        if (!kits.containsKey(normalized)) return false;
        YamlConfiguration root = YamlConfiguration.loadConfiguration(file);
        root.set("kits." + normalized, null);
        try { root.save(file); reload(); return true; }
        catch (IOException exception) { plugin.getLogger().warning("Could not delete admin kit " + normalized + ": " + exception.getMessage()); return false; }
    }

    private static KitDefinition load(String id, ConfigurationSection section) {
        Map<Integer, ItemStack> inventory = new LinkedHashMap<>();
        ConfigurationSection inv = section.getConfigurationSection("inventory");
        if (inv != null) for (String key : inv.getKeys(false)) {
            try { int slot = Integer.parseInt(key); ItemStack item = inv.getItemStack(key); if (slot >= 0 && slot < 36 && item != null) inventory.put(slot, item.clone()); }
            catch (NumberFormatException ignored) { }
        }
        ConfigurationSection armor = section.getConfigurationSection("armor");
        ItemStack[] armorContents = new ItemStack[]{ item(armor, "boots"), item(armor, "leggings"), item(armor, "chestplate"), item(armor, "helmet") };
        Material icon = material(section.getString("icon"));
        if (icon == null) icon = Material.CHEST;
        GameMode gameMode;
        try { gameMode = GameMode.valueOf(section.getString("game-mode", "SURVIVAL").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { gameMode = GameMode.SURVIVAL; }
        return new KitDefinition(id, section.getString("display-name", id), icon, section.getString("arena", ""), Map.copyOf(inventory), armorContents, item(section, "offhand"), gameMode, section.getBoolean("enabled", true));
    }

    private static ItemStack item(ConfigurationSection section, String key) { return section == null ? null : cloneOrNull(section.getItemStack(key)); }
    private static ItemStack cloneOrNull(ItemStack item) { return item == null ? null : item.clone(); }
    private static Material material(String value) { return value == null ? null : Material.matchMaterial(value); }
    private static Material firstMaterial(ItemStack[] items) { for (ItemStack item : items) if (item != null && !item.getType().isAir()) return item.getType(); return null; }
    private static String normalize(String id) { return id == null ? "" : id.trim().toLowerCase(Locale.ROOT); }
}
