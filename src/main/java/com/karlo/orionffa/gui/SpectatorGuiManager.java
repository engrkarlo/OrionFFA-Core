package com.karlo.orionffa.gui;

import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.ffa.ServiceResult;
import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.player.FfaState;
import com.karlo.orionffa.player.PlayerSession;
import com.karlo.orionffa.player.PlayerSessionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owns the isolated spectator selector and spectator hotbar. */
public final class SpectatorGuiManager {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final FfaService ffa;
    private final PlayerSessionManager sessions;
    private final ArenaManager arenas;
    private final LobbyMenuManager lobbyMenus;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;
    private final NamespacedKey spectatorKey;

    public SpectatorGuiManager(JavaPlugin plugin, MessageService messages, FfaService ffa,
                               PlayerSessionManager sessions, ArenaManager arenas,
                               LobbyMenuManager lobbyMenus) {
        this.plugin = plugin;
        this.messages = messages;
        this.ffa = ffa;
        this.sessions = sessions;
        this.arenas = arenas;
        this.lobbyMenus = lobbyMenus;
        this.actionKey = new NamespacedKey(plugin, "spectator-action");
        this.targetKey = new NamespacedKey(plugin, "spectator-target");
        this.spectatorKey = new NamespacedKey(plugin, "spectator-hotbar");
    }

    public boolean owns(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof Holder;
    }

    public void open(Player player) {
        YamlConfiguration config = loadConfig();
        ConfigurationSection menu = config.getConfigurationSection("menus.spectator_selector");
        if (menu == null) return;
        int rows = Math.max(1, Math.min(6, menu.getInt("rows", 6)));
        Inventory inventory = Bukkit.createInventory(new Holder(), rows * 9,
                messages.component(menu.getString("title", "<dark_gray>Select a Player>")));
        fill(inventory, menu.getConfigurationSection("filler"));

        ConfigurationSection back = menu.getConfigurationSection("items.back");
        addConfigured(inventory, back, "back", "");
        int backSlot = back == null ? -1 : back.getInt("slot", -1);

        // Filler intentionally occupies the whole GUI. Target entries therefore replace
        // filler rather than searching for an empty slot.
        int slot = 0;
        for (PlayerSession session : sessions.active()) {
            if (session.playerId().equals(player.getUniqueId())) continue;
            if (!isValidSession(session)) continue;
            Player target = Bukkit.getPlayer(session.playerId());
            if (target == null || !target.isOnline() || target.isDead()) continue;

            while (slot < inventory.getSize() && slot == backSlot) slot++;
            if (slot >= inventory.getSize()) break;
            inventory.setItem(slot++, item(Material.PLAYER_HEAD, "<light_purple>" + target.getName(),
                    List.of("<gray>Arena: <white>" + session.arenaId(), "<yellow>Click to spectate."),
                    "spectate", target.getUniqueId().toString()));
        }
        player.openInventory(inventory);
    }

    public void handle(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String target = item.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (action == null) return;
        if ("back".equals(action)) {
            player.closeInventory();
            return;
        }
        if (!"spectate".equals(action)) return;
        try {
            Player targetPlayer = Bukkit.getPlayer(UUID.fromString(target == null ? "" : target));
            if (targetPlayer == null || !isValidTarget(targetPlayer)) {
                messages.send(player, "spectate-unavailable");
                return;
            }
            ServiceResult result = ffa.startSpectating(player, targetPlayer);
            if (result.success()) {
                player.closeInventory();
                applyHotbar(player);
            } else messages.send(player, result.messageKey());
        } catch (IllegalArgumentException ignored) {
            messages.send(player, "spectate-unavailable");
        }
    }

    private boolean isValidSession(PlayerSession session) {
        if (session.state() != FfaState.FFA) return false;
        if (session.arenaId() == null || session.arenaId().isBlank()) return false;
        return arenas.get(session.arenaId()).map(arena -> arena.enabled()).orElse(false);
    }

    public boolean isValidTarget(Player target) {
        Optional<PlayerSession> found = sessions.get(target.getUniqueId());
        return found.isPresent() && isValidSession(found.get());
    }

    public boolean isSpectating(Player player) {
        return sessions.get(player.getUniqueId()).map(s -> s.state() == FfaState.SPECTATING).orElse(false);
    }

    public boolean isSpectatorItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(spectatorKey, PersistentDataType.BYTE);
    }

    public String spectatorAction(ItemStack item) {
        if (!isSpectatorItem(item)) return "";
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(actionKey, PersistentDataType.STRING, "");
    }

    public void applyHotbar(Player player) {
        if (!isSpectating(player)) return;
        YamlConfiguration config = loadConfig();
        ConfigurationSection items = config.getConfigurationSection("menus.spectating.items");
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        if (items == null) {
            player.updateInventory();
            return;
        }
        for (String id : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(id);
            if (item == null) continue;
            int slot = item.getInt("slot", -1);
            if (slot < 0 || slot > 8) continue;
            player.getInventory().setItem(slot, spectatorItem(item));
        }
        player.updateInventory();
    }

    public void exit(Player player) {
        ServiceResult result = ffa.leaveToLobby(player);
        if (result.success()) {
            player.closeInventory();
            lobbyMenus.apply(player);
        } else messages.send(player, result.messageKey());
    }

    private ItemStack spectatorItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(section.getString("name", "<red>Invalid item")));
        meta.lore(section.getStringList("lore").stream().map(messages::component).toList());
        meta.getPersistentDataContainer().set(spectatorKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, section.getString("action", ""));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore, String action, String target) {
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(name));
        meta.lore(lore.stream().map(messages::component).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target);
        item.setItemMeta(meta);
        return item;
    }

    private void addConfigured(Inventory inventory, ConfigurationSection section, String action, String target) {
        if (section == null) return;
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) return;
        Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
        inventory.setItem(slot, item(material == null ? Material.BARRIER : material,
                section.getString("name", "<red>Back"), section.getStringList("lore"), action, target));
    }

    private void fill(Inventory inventory, ConfigurationSection filler) {
        if (filler == null || !filler.getBoolean("enabled", true)) return;
        Material material = Material.matchMaterial(filler.getString("material", "GRAY_STAINED_GLASS_PANE"));
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack pane = item(material, filler.getString("name", " "), filler.getStringList("lore"), "none", "");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane.clone());
    }

    private YamlConfiguration loadConfig() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "guis.yml"));
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
