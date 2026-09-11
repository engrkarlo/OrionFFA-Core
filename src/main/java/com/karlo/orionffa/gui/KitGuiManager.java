package com.karlo.orionffa.gui;

import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.kit.KitDefinition;
import com.karlo.orionffa.kit.KitManager;
import com.karlo.orionffa.kit.KitPersistenceManager;
import com.karlo.orionffa.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Built-in kit selector/editor. Public kits are owned by OrionFFA; player preferences are per-player. */
public final class KitGuiManager {
    private final JavaPlugin plugin;
    private final MessageService messages;
    private final FfaService ffa;
    private final KitManager kits;
    private final KitPersistenceManager customKits;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;
    private final Map<UUID, String> naming = new ConcurrentHashMap<>();
    private YamlConfiguration definitions;

    public KitGuiManager(JavaPlugin plugin, MessageService messages, FfaService ffa, KitManager kits, KitPersistenceManager customKits) {
        this.plugin = plugin; this.messages = messages; this.ffa = ffa; this.kits = kits; this.customKits = customKits;
        this.actionKey = new NamespacedKey(plugin, "kit_gui_action");
        this.targetKey = new NamespacedKey(plugin, "kit_gui_target");
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "guis.yml");
        if (!file.exists()) plugin.saveResource("guis.yml", false);
        definitions = YamlConfiguration.loadConfiguration(file);
    }

    public boolean owns(Inventory inventory) { return inventory != null && inventory.getHolder(false) instanceof KitGuiHolder; }

    public void openSelector(Player player) {
        Inventory inv = menu("kit_selector", "Select a Kit");
        filler(inv, "kit_selector");
        int slot = 0;
        for (KitDefinition kit : kits.available()) {
            if (slot >= inv.getSize() - 9) break;
            boolean custom = customKits.has(player.getUniqueId(), kit.id());
            List<String> lore = custom ? List.of("<gray>Your saved preference is available.", "<yellow>Click to choose default or your preference.") : List.of("<gray>Use the admin kit.", "<yellow>Click to select.");
            inv.setItem(slot++, item(kit.icon(), kit.displayName(), lore, custom ? "choose_preference" : "join", kit.id()));
        }
        addBack(inv, "kit_selector", Math.min(inv.getSize() - 1, 49));
        player.openInventory(inv);
    }

    public void openEditor(Player player) {
        Inventory inv = menu("kit_editor", "Edit Kits");
        filler(inv, "kit_editor");
        int slot = configuredSlot("kit_editor.admin-kits-start", 10);
        for (KitDefinition kit : kits.available()) {
            if (slot >= inv.getSize() - 9) break;
            boolean custom = customKits.has(player.getUniqueId(), kit.id());
            List<String> lore = custom ? List.of("<gray>Your personal preference is saved.", "<yellow>Click to edit or delete it.") : List.of("<gray>Edit this admin kit.", "<yellow>Click to continue.");
            inv.setItem(slot++, item(kit.icon(), kit.displayName(), lore, custom ? "personal_menu" : "edit_admin", kit.id()));
        }
        if (player.hasPermission("orionffa.admin")) inv.setItem(configuredSlot("kit_editor.create.slot", 4), configured("kit_editor.create", "create_admin", ""));
        addBack(inv, "kit_editor", Math.min(inv.getSize() - 1, 49));
        player.openInventory(inv);
    }

    private void openPreference(Player player, String kitId) {
        Optional<KitDefinition> kit = kits.find(kitId);
        if (kit.isEmpty()) { player.closeInventory(); messages.send(player, "kit-unavailable"); return; }
        Inventory inv = menu("kit_preference", "Choose " + kit.get().id());
        filler(inv, "kit_preference");
        inv.setItem(configuredSlot("kit_preference.default.slot", 11), configured("kit_preference.default", "join_default", kitId));
        inv.setItem(configuredSlot("kit_preference.custom.slot", 15), configured("kit_preference.custom", "join_custom", kitId));
        inv.setItem(configuredSlot("kit_preference.delete.slot", 22), configured("kit_preference.delete", "delete_personal", kitId));
        addBack(inv, "kit_preference", Math.min(inv.getSize() - 1, 26));
        player.openInventory(inv);
    }

    public void handle(Player player, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        PersistentDataContainer data = clicked.getItemMeta().getPersistentDataContainer();
        String action = data.get(actionKey, PersistentDataType.STRING);
        String target = data.get(targetKey, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "join", "join_custom" -> { player.closeInventory(); respond(player, ffa.joinKit(player, target)); }
            case "choose_preference" -> openPreference(player, target);
            case "join_default" -> { customKits.useDefaultOnce(player.getUniqueId(), target); player.closeInventory(); respond(player, ffa.joinKit(player, target)); }
            case "delete_personal" -> { customKits.delete(player.getUniqueId(), target); messages.send(player, "kit-preference-deleted", Map.of("kit", target)); openEditor(player); }
            case "edit_admin" -> { customKits.useDefaultOnce(player.getUniqueId(), target); player.closeInventory(); respond(player, ffa.editKit(player, target)); }
            case "personal_menu" -> openPreference(player, target);
            case "create_admin" -> beginCreate(player);
            case "back" -> player.closeInventory();
            default -> { }
        }
    }

    private void beginCreate(Player player) {
        if (!player.hasPermission("orionffa.admin")) return;
        player.closeInventory(); player.getInventory().clear(); naming.put(player.getUniqueId(), "admin");
        player.sendMessage(messages.component("<gold>Kit creation</gold> <gray>— arrange the inventory for the new kit, then type its name in chat."));
        player.sendMessage(messages.component("<gray>Allowed: letters, numbers, underscore and hyphen. Type <red>cancel</red> to abort."));
    }

    public void chat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!naming.containsKey(id)) return;
        event.setCancelled(true);
        String name = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
        Player player = event.getPlayer(); naming.remove(id);
        if (name.equals("cancel")) { Bukkit.getScheduler().runTask(plugin, () -> { messages.send(player, "kit-create-cancelled"); ffa.enterLobby(player); }); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (kits.saveFromPlayer(player, name)) { messages.send(player, "kit-admin-created", Map.of("kit", name)); ffa.enterLobby(player); }
            else player.sendMessage(messages.component("<red>Could not create that kit. The name may already exist or be invalid.</red>"));
        });
    }

    private Inventory menu(String id, String fallbackTitle) {
        ConfigurationSection section = definitions.getConfigurationSection("menus." + id);
        int rows = section == null ? 3 : Math.max(1, Math.min(6, section.getInt("rows", 3)));
        String title = section == null ? fallbackTitle : section.getString("title", fallbackTitle);
        KitGuiHolder holder = new KitGuiHolder(id); Inventory inventory = Bukkit.createInventory(holder, rows * 9, messages.component(title)); holder.inventory(inventory); return inventory;
    }

    private void filler(Inventory inventory, String menu) {
        ConfigurationSection section = definitions.getConfigurationSection("menus." + menu + ".filler");
        if (section == null || !section.getBoolean("enabled", true)) return;
        ItemStack fill = configured(section, "none", "");
        for (int i = 0; i < inventory.getSize(); i++) if (inventory.getItem(i) == null) inventory.setItem(i, fill.clone());
    }

    private ItemStack configured(String path, String action, String target) { ConfigurationSection section = definitions.getConfigurationSection("menus." + path); return section == null ? item(Material.BARRIER, "<red>Missing configuration", List.of(), action, target) : configured(section, action, target); }
    private ItemStack configured(ConfigurationSection section, String action, String target) { Material material = Material.matchMaterial(section.getString("material", "BARRIER")); return item(material == null ? Material.BARRIER : material, section.getString("name", "Item"), section.getStringList("lore"), action, target); }

    private ItemStack item(Material material, String name, List<String> lore, String action, String target) {
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material); ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(name)); meta.lore(lore.stream().map(messages::component).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action); meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target); item.setItemMeta(meta); return item;
    }

    private void addBack(Inventory inventory, String menu, int fallback) { ConfigurationSection section = definitions.getConfigurationSection("menus." + menu + ".back"); int slot = section == null ? fallback : section.getInt("slot", fallback); ItemStack back = section == null ? item(Material.ARROW, "<yellow>Back", List.of(), "back", "") : configured(section, "back", ""); if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, back); }
    private int configuredSlot(String path, int fallback) { return Math.max(0, definitions.getInt("menus." + path, fallback)); }
    private void respond(Player player, com.karlo.orionffa.ffa.ServiceResult result) { messages.send(player, result.messageKey(), result.placeholders()); }
}
