package com.karlo.orionffa.gui;

import com.karlo.orionffa.kit.KitDefinition;
import com.karlo.orionffa.kit.KitManager;
import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.party.Party;
import com.karlo.orionffa.party.PartyManager;
import com.karlo.orionffa.party.PartyMatchService;
import com.karlo.orionffa.party.PartyResult;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class PartySplitGuiManager {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final PartyManager parties;
    private final PartyMatchService matches;
    private final KitManager kits;
    private final NamespacedKey ownerKey = new NamespacedKey("orionffa", "party_split_action");

    public PartySplitGuiManager(JavaPlugin plugin, MessageService messages, PartyManager parties, PartyMatchService matches, KitManager kits) {
        this.plugin = plugin;
        this.messages = messages;
        this.parties = parties;
        this.matches = matches;
        this.kits = kits;
    }

    public boolean owns(Inventory inventory) { return inventory.getHolder(false) instanceof Holder; }

    public void open(Player player) {
        Party party = parties.find(player.getUniqueId()).orElse(null);
        if (party == null) { player.sendMessage(messages.component("<red>You are not in a party.</red>")); return; }
        if (!party.leader().equals(player.getUniqueId())) { player.sendMessage(messages.component("<red>Only the party leader can split the party.</red>")); return; }
        if (party.members().size() < 2) { player.sendMessage(messages.component("<red>Your party needs at least 2 players.</red>")); return; }
        kits.reload();
        YamlConfiguration config = load();
        ConfigurationSection menu = config.getConfigurationSection("menus.party_split");
        int rows = menu == null ? 3 : Math.max(1, Math.min(6, menu.getInt("rows", 3)));
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, messages.component(menu == null ? "<dark_gray>Choose Party Kit" : menu.getString("title", "<dark_gray>Choose Party Kit")));
        holder.inventory = inventory;
        int slot = 0;
        for (KitDefinition kit : kits.available()) {
            if (slot >= inventory.getSize()) break;
            while (slot < inventory.getSize() && inventory.getItem(slot) != null) slot++;
            if (slot >= inventory.getSize()) break;
            ItemStack item = new ItemStack(kit.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(messages.component(kit.displayName()));
            meta.lore(List.of(messages.component("<gray>Split the party and fight using this kit."), messages.component("<yellow>Click to select.")));
            meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, kit.id());
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }
        fillEmpty(inventory, menu == null ? null : menu.getConfigurationSection("filler"));
        player.openInventory(inventory);
    }

    public void handle(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String kit = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (kit == null) return;
        player.closeInventory();
        PartyResult result = matches.start(player, true, kit);
        player.sendMessage(messages.component(result.success() ? "<green>Party split match started.</green>" : "<red>" + result.reason() + "</red>"));
    }

    private YamlConfiguration load() {
        File file = new File(plugin.getDataFolder(), "guis.yml");
        if (!file.exists()) plugin.saveResource("guis.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    private void fillEmpty(Inventory inventory, ConfigurationSection filler) {
        if (filler == null || !filler.getBoolean("enabled", true)) return;
        Material material = Material.matchMaterial(filler.getString("material", "GRAY_STAINED_GLASS_PANE"));
        ItemStack item = new ItemStack(material == null ? Material.GRAY_STAINED_GLASS_PANE : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(filler.getString("name", " ")));
        meta.lore(filler.getStringList("lore").stream().map(messages::component).toList());
        item.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) if (inventory.getItem(i) == null) inventory.setItem(i, item.clone());
    }

    private static final class Holder implements org.bukkit.inventory.InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}