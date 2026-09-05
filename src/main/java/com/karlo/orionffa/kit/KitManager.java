package com.karlo.orionffa.kit;

import com.karlo.orionffa.config.ConfigManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class KitManager {
    private final ConfigManager config;
    private Map<String, KitDefinition> kits = Map.of();

    public KitManager(ConfigManager config) {
        this.config = config;
        reload();
    }

    public void reload() {
        ConfigurationSection root = config.file().getConfigurationSection("kits");
        if (root == null) {
            kits = Map.of();
            return;
        }
        Map<String, KitDefinition> loaded = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            loaded.put(id.toLowerCase(Locale.ROOT), load(id.toLowerCase(Locale.ROOT), section));
        }
        kits = Map.copyOf(loaded);
    }

    public Optional<KitDefinition> find(String id) {
        return Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT))).filter(KitDefinition::enabled);
    }

    public boolean existsAndEnabled(String id) {
        return find(id).isPresent();
    }

    public List<KitDefinition> available() {
        return kits.values().stream().filter(KitDefinition::enabled).sorted(Comparator.comparing(KitDefinition::id)).toList();
    }

    public List<String> names() {
        return available().stream().map(KitDefinition::id).toList();
    }

    private static KitDefinition load(String id, ConfigurationSection section) {
        Map<Integer, ItemStack> inventory = new LinkedHashMap<>();
        for (Map<?, ?> entry : section.getMapList("inventory")) {
            Object slot = entry.get("slot");
            Material material = material(entry.get("material"));
            if (!(slot instanceof Number number) || material == null) continue;
            int index = number.intValue();
            if (index >= 0 && index < 36) inventory.put(index, new ItemStack(material, amount(entry.get("amount"))));
        }
        ConfigurationSection armor = section.getConfigurationSection("armor");
        ItemStack[] armorContents = new ItemStack[]{
                item(armor, "boots"), item(armor, "leggings"), item(armor, "chestplate"), item(armor, "helmet")};
        Material icon = material(section.getString("icon"));
        if (icon == null) icon = Material.CHEST;
        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(section.getString("game-mode", "SURVIVAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            gameMode = GameMode.SURVIVAL;
        }
        return new KitDefinition(id, section.getString("display-name", id), icon, section.getString("arena", ""),
                Map.copyOf(inventory), armorContents, item(section, "offhand"), gameMode, section.getBoolean("enabled", true));
    }

    private static ItemStack item(ConfigurationSection section, String key) {
        if (section == null) return null;
        Material material = material(section.get(key));
        return material == null ? null : new ItemStack(material);
    }

    private static Material material(Object value) {
        return value == null ? null : Material.matchMaterial(String.valueOf(value));
    }

    private static int amount(Object value) {
        return value instanceof Number number ? Math.clamp(number.intValue(), 1, 64) : 1;
    }
}
