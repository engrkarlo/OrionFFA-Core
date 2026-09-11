package com.karlo.orionffa.gui;

import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.party.Party;
import com.karlo.orionffa.party.PartyManager;
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

/** GUI for sending party invites and accepting pending invitations. */
public final class PartyInviteGuiManager {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final PartyManager parties;
    private final ConfigManager config;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;

    public PartyInviteGuiManager(JavaPlugin plugin, MessageService messages, PartyManager parties, ConfigManager config) {
        this.plugin = plugin;
        this.messages = messages;
        this.parties = parties;
        this.config = config;
        this.actionKey = new NamespacedKey(plugin, "party_invite_action");
        this.targetKey = new NamespacedKey(plugin, "party_invite_target");
    }

    public boolean owns(Inventory inventory) {
        return inventory.getHolder(false) instanceof Holder;
    }

    public void openTargets(Player leader) {
        Party party = parties.find(leader.getUniqueId()).orElse(null);
        if (party == null) { leader.sendMessage(messages.component("<red>Create a party first.</red>")); return; }
        if (!party.leader().equals(leader.getUniqueId())) { leader.sendMessage(messages.component("<red>Only the party leader can invite players.</red>")); return; }
        if (party.members().size() >= 64) { leader.sendMessage(messages.component("<red>Your party is full.</red>")); return; }
        YamlConfiguration root = load();
        ConfigurationSection menu = root.getConfigurationSection("menus.party_invite");
        Inventory inventory = create(menu, "Invite Players");
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(leader) || !config.isLobbyWorld(target) || parties.find(target.getUniqueId()).isPresent()) continue;
            if (slot >= inventory.getSize()) break;
            while (slot < inventory.getSize() && inventory.getItem(slot) != null) slot++;
            if (slot >= inventory.getSize()) break;
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) skull.setOwningPlayer(target);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(messages.component("<aqua>" + target.getName()));
            meta.lore(List.of(messages.component("<gray>Invite this player to your party."), messages.component("<yellow>Click to invite.")));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "invite");
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }
        leader.openInventory(inventory);
    }

    public void openInvitations(Player player) {
        List<java.util.UUID> leaders = parties.pendingInviteLeaders(player.getUniqueId());
        YamlConfiguration root = load();
        ConfigurationSection menu = root.getConfigurationSection("menus.party_invitations");
        Inventory inventory = create(menu, "Party Invitations");
        int slot = 0;
        for (java.util.UUID leaderId : leaders) {
            Player leader = Bukkit.getPlayer(leaderId);
            if (leader == null) continue;
            if (slot >= inventory.getSize()) break;
            while (slot < inventory.getSize() && inventory.getItem(slot) != null) slot++;
            if (slot >= inventory.getSize()) break;
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            if (item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) skull.setOwningPlayer(leader);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(messages.component("<green>Invitation from " + leader.getName()));
            meta.lore(List.of(messages.component("<gray>Click to accept this party invitation.")));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "accept");
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, leaderId.toString());
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }
        if (leaders.isEmpty()) player.sendMessage(messages.component("<yellow>You have no pending party invitations.</yellow>"));
        player.openInventory(inventory);
    }

    public void handle(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        var data = item.getItemMeta().getPersistentDataContainer();
        String action = data.get(actionKey, PersistentDataType.STRING);
        String target = data.get(targetKey, PersistentDataType.STRING);
        if (action == null || target == null) return;
        java.util.UUID targetId;
        try { targetId = java.util.UUID.fromString(target); } catch (IllegalArgumentException ignored) { return; }
        if ("invite".equals(action)) {
            Player invited = Bukkit.getPlayer(targetId);
            if (invited == null || !config.isLobbyWorld(invited)) { player.sendMessage(messages.component("<red>That player is no longer available in the lobby.</red>")); return; }
            PartyResult result = parties.invite(player.getUniqueId(), targetId);
            if (result.success()) {
                messages.send(player, "party-invite", java.util.Map.of("player", invited.getName()));
                messages.send(invited, "party-invited", java.util.Map.of("player", player.getName()));
            } else player.sendMessage(messages.component("<red>" + result.reason() + "</red>"));
            openTargets(player);
        } else if ("accept".equals(action)) {
            PartyResult result = parties.join(player.getUniqueId(), targetId);
            if (result.success()) {
                messages.send(player, "party-joined");
                player.closeInventory();
                player.sendMessage(messages.component("<green>You joined the party. Use the Party item to enter party mode.</green>"));
            } else player.sendMessage(messages.component("<red>" + result.reason() + "</red>"));
        }
    }

    private Inventory create(ConfigurationSection menu, String fallbackTitle) {
        int rows = menu == null ? 6 : Math.max(1, Math.min(6, menu.getInt("rows", 6)));
        String title = menu == null ? "<dark_gray>" + fallbackTitle : menu.getString("title", "<dark_gray>" + fallbackTitle);
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, messages.component(title));
        holder.inventory = inventory;
        if (menu != null) fill(inventory, menu.getConfigurationSection("filler"));
        return inventory;
    }

    private void fill(Inventory inventory, ConfigurationSection filler) {
        if (filler == null || !filler.getBoolean("enabled", true)) return;
        Material material = Material.matchMaterial(filler.getString("material", "GRAY_STAINED_GLASS_PANE"));
        ItemStack item = new ItemStack(material == null ? Material.GRAY_STAINED_GLASS_PANE : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(filler.getString("name", " ")));
        meta.lore(filler.getStringList("lore").stream().map(messages::component).toList());
        item.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, item.clone());
    }

    private YamlConfiguration load() {
        File file = new File(plugin.getDataFolder(), "guis.yml");
        if (!file.exists()) plugin.saveResource("guis.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    private static final class Holder implements org.bukkit.inventory.InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
