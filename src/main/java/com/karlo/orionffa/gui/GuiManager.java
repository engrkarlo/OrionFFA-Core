package com.karlo.orionffa.gui;

import com.karlo.orionffa.arena.Arena;
import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.arena.ArenaResetService;
import com.karlo.orionffa.config.ArenaSelectionMode;
import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.ffa.ServiceResult;
import com.karlo.orionffa.kit.KitDefinition;
import com.karlo.orionffa.kit.KitManager;
import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.party.Party;
import com.karlo.orionffa.party.PartyManager;
import com.karlo.orionffa.party.PartyResult;
import com.karlo.orionffa.player.FfaState;
import com.karlo.orionffa.player.PlayerSession;
import com.karlo.orionffa.player.PlayerSessionManager;
import com.karlo.orionffa.statistics.PlayerStatistics;
import com.karlo.orionffa.statistics.StatisticsManager;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GuiManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MessageService messages;
    private final FfaService ffa;
    private final KitManager kits;
    private final ArenaManager arenas;
    private final ArenaResetService arenaReset;
    private final PlayerSessionManager sessions;
    private final PartyManager parties;
    private final StatisticsManager statistics;
    private final NamespacedKey guiKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;
    private final Map<UUID, GuiSession> open = new HashMap<>();
    private final Map<UUID, Long> actionLocks = new HashMap<>();
    private YamlConfiguration definitions;

    public GuiManager(JavaPlugin plugin, ConfigManager config, MessageService messages, FfaService ffa, KitManager kits,
                      ArenaManager arenas, ArenaResetService arenaReset, PlayerSessionManager sessions,
                      PartyManager parties, StatisticsManager statistics) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.ffa = ffa;
        this.kits = kits;
        this.arenas = arenas;
        this.arenaReset = arenaReset;
        this.sessions = sessions;
        this.parties = parties;
        this.statistics = statistics;
        guiKey = new NamespacedKey(plugin, "gui");
        actionKey = new NamespacedKey(plugin, "action");
        targetKey = new NamespacedKey(plugin, "target");
        reload();
    }

    public boolean reload() {
        File file = new File(plugin.getDataFolder(), "guis.yml");
        if (!file.exists()) plugin.saveResource("guis.yml", false);
        YamlConfiguration candidate = YamlConfiguration.loadConfiguration(file);
        try {
            validate(candidate);
            definitions = candidate;
            return true;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().severe("Invalid GUI configuration: " + exception.getMessage());
            return false;
        }
    }

    public void openKits(Player player) {
        Inventory inventory = inventory(GuiType.KITS, "kit_selector");
        addConfigured(inventory, "kit_selector", "back", GuiType.KITS, "back", "");
        int slot = 0;
        for (KitDefinition kit : kits.available()) {
            slot = nextFreeSlot(inventory, slot);
            if (slot < 0) break;
            ItemStack item = new ItemStack(kit.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(messages.component(kit.displayName()));
            meta.lore(List.of(messages.component("<gray>Click to join <white>" + kit.id() + "</white>.")));
            mark(meta.getPersistentDataContainer(), GuiType.KITS, "join_kit", kit.id());
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }
        show(player, inventory, GuiType.KITS);
    }

    private void openKitsForArena(Player player, String arenaId) {
        Optional<Arena> found = arenas.get(arenaId);
        if (found.isEmpty()) {
            messages.send(player, "arena-unavailable");
            return;
        }
        Arena arena = found.get();
        Inventory inventory = inventory(GuiType.KITS, "kit_selector");
        addConfigured(inventory, "kit_selector", "back", GuiType.KITS, "back", "");
        int slot = 0;
        for (KitDefinition kit : kits.available()) {
            if (!arena.supports(kit.id())) continue;
            slot = nextFreeSlot(inventory, slot);
            if (slot < 0) break;
            inventory.setItem(slot++, actionItem(kit.icon(), kit.displayName(),
                    List.of("<gray>Arena: <white>" + arena.id(), "<yellow>Click to join."),
                    GuiType.KITS, "join_arena", kit.id() + "|" + arena.id()));
        }
        show(player, inventory, GuiType.KITS, "arena-kit:" + arena.id());
    }

    public void openKitEditor(Player player) {
        Inventory inventory = inventory(GuiType.KITS, "kit_editor_selector");
        addConfigured(inventory, "kit_editor_selector", "back", GuiType.KITS, "back", "");
        int slot = 0;
        for (KitDefinition kit : kits.available()) {
            slot = nextFreeSlot(inventory, slot);
            if (slot < 0) break;
            ItemStack item = new ItemStack(kit.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(messages.component(kit.displayName()));
            meta.lore(List.of(messages.component("<gray>Click to edit <white>" + kit.id() + "</white>.")));
            mark(meta.getPersistentDataContainer(), GuiType.KITS, "edit_kit", kit.id());
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }
        show(player, inventory, GuiType.KITS, "editor");
    }

    public void openSpectators(Player player) {
        Inventory inventory = inventory(GuiType.SPECTATORS, "spectator_selector");
        addConfigured(inventory, "spectator_selector", "back", GuiType.SPECTATORS, "back", "");
        int slot = 0;
        for (PlayerSession session : sessions.active()) {
            slot = nextFreeSlot(inventory, slot);
            if (slot < 0) break;
            if (session.playerId().equals(player.getUniqueId()) || session.state() != FfaState.FFA) continue;
            Player target = Bukkit.getPlayer(session.playerId());
            if (target == null || !target.isOnline()) continue;
            inventory.setItem(slot++, actionItem(Material.PLAYER_HEAD, "<light_purple>" + target.getName(),
                    List.of("<gray>Click to spectate."), GuiType.SPECTATORS, "spectate", target.getUniqueId().toString()));
        }
        show(player, inventory, GuiType.SPECTATORS);
    }

    public void openParty(Player player) {
        Inventory inventory = inventory(GuiType.PARTY, "party");
        Optional<Party> party = parties.find(player.getUniqueId());
        if (party.isEmpty()) {
            inventory.setItem(13, actionItem(Material.LIME_WOOL, "<green>Create a Party", List.of("<gray>Start a party with your friends."), GuiType.PARTY, "party_create", ""));
        } else {
            Party current = party.get();
            inventory.setItem(11, actionItem(Material.PLAYER_HEAD, "<aqua>Members: " + current.members().size(), List.of("<gray>Leader: <white>" + playerName(current.leader())), GuiType.PARTY, "none", ""));
            inventory.setItem(13, actionItem(Material.PAPER, "<yellow>Invite Players", List.of("<gray>Choose an online player."), GuiType.PARTY, "open_party_invites", ""));
            inventory.setItem(15, actionItem(Material.BOOK, "<light_purple>Party Chat", List.of("<gray>Toggle party-only chat."), GuiType.PARTY, "party_chat", ""));
            inventory.setItem(22, actionItem(Material.BARRIER, "<red>Leave Party", List.of("<gray>Leave your current party."), GuiType.PARTY, "party_leave", ""));
        }
        addConfigured(inventory, "party", "back", GuiType.PARTY, "back", "");
        show(player, inventory, GuiType.PARTY);
    }

    public void openPartyInvites(Player player) {
        Optional<Party> party = parties.find(player.getUniqueId());
        if (party.isEmpty() || !party.get().leader().equals(player.getUniqueId())) {
            messages.send(player, "party-error", Map.of("reason", "Only the party leader can invite players."));
            return;
        }
        Inventory inventory = inventory(GuiType.PARTY_INVITES, "party_invites");
        addConfigured(inventory, "party_invites", "back", GuiType.PARTY_INVITES, "back", "");
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            slot = nextFreeSlot(inventory, slot);
            if (slot < 0) break;
            if (target.equals(player) || parties.find(target.getUniqueId()).isPresent()) continue;
            inventory.setItem(slot++, actionItem(Material.PLAYER_HEAD, "<green>" + target.getName(), List.of("<gray>Click to invite."), GuiType.PARTY_INVITES, "party_invite", target.getUniqueId().toString()));
        }
        show(player, inventory, GuiType.PARTY_INVITES);
    }

    public void openStatistics(Player player) {
        Inventory inventory = inventory(GuiType.STATISTICS, "statistics");
        PlayerStatistics stats = statistics.get(player.getUniqueId());
        inventory.setItem(13, actionItem(Material.BOOK, "<gold>Your Statistics", List.of(
                "<gray>Kills: <white>" + stats.kills(), "<gray>Deaths: <white>" + stats.deaths(),
                "<gray>Streak: <white>" + stats.killStreak(), "<gray>Best streak: <white>" + stats.bestKillStreak(),
                "<gray>KD: <white>" + String.format(java.util.Locale.ROOT, "%.2f", stats.kd())), GuiType.STATISTICS, "none", ""));
        addConfigured(inventory, "statistics", "back", GuiType.STATISTICS, "back", "");
        show(player, inventory, GuiType.STATISTICS);
    }

    /** Shows every configured arena, not just available arenas. */
    public void openArenas(Player player, String kitId) { openArenas(player, kitId, 0); }

    private void openArenas(Player player, String kitId, int page) {
        List<Arena> all = arenas.all();
        int pageSize = 45;
        int pages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int currentPage = Math.clamp(page, 0, pages - 1);
        Inventory inventory = inventory(GuiType.ARENAS, "arena_selector");
        int slot = 0;
        int start = currentPage * pageSize;
        int end = Math.min(all.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            Arena arena = all.get(index);
            boolean compatible = kitId == null || kitId.isBlank() || arena.supports(kitId);
            boolean world = arena.spawn().resolve().isPresent();
            boolean resetting = arenaReset.isResetting(arena.id());
            boolean full = arena.occupants() >= arena.capacity();
            boolean available = arena.enabled() && !arena.locked() && compatible && world && !resetting && !full;
            Material material = available ? Material.LIME_WOOL : Material.RED_WOOL;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Players: <white>" + arena.occupants() + "/" + arena.capacity());
            if (available) lore.add("<gray>Status: <green>Available");
            else if (arena.locked()) lore.add("<gray>Status: <red>Locked");
            else if (resetting) lore.add("<gray>Status: <red>Resetting");
            else if (full) lore.add("<gray>Status: <red>Full");
            else if (!world) lore.add("<gray>Status: <red>World unavailable");
            else if (!compatible) lore.add("<gray>Status: <red>Kit incompatible");
            else lore.add("<gray>Status: <red>Unavailable");
            if (available) lore.add("<yellow>Click to select");
            inventory.setItem(slot++, actionItem(material, (available ? "<green>" : "<red>") + arena.id(), lore,
                    GuiType.ARENAS, available ? (kitId == null || kitId.isBlank() ? "select_arena" : "join_arena") : "none",
                    available ? (kitId == null || kitId.isBlank() ? arena.id() : kitId + "|" + arena.id()) : ""));
        }
        if (currentPage > 0) inventory.setItem(45, actionItem(Material.ARROW, "<yellow>Previous", List.of("<gray>Page " + currentPage + "/" + pages), GuiType.ARENAS, "arena_prev", String.valueOf(currentPage - 1)));
        if (currentPage + 1 < pages) inventory.setItem(53, actionItem(Material.ARROW, "<yellow>Next", List.of("<gray>Page " + (currentPage + 2) + "/" + pages), GuiType.ARENAS, "arena_next", String.valueOf(currentPage + 1)));
        addConfigured(inventory, "arena_selector", "back", GuiType.ARENAS, "back", "");
        show(player, inventory, GuiType.ARENAS, "arena:" + (kitId == null ? "" : kitId) + ":" + currentPage);
    }

    public void handle(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta() || locked(player.getUniqueId())) return;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        String action = data.get(actionKey, PersistentDataType.STRING);
        String target = data.get(targetKey, PersistentDataType.STRING);
        if (action == null) return;
        try {
            switch (action) {
                case "none" -> { }
                case "back" -> {
                    GuiSession session = open.get(player.getUniqueId());
                    if (session != null && session.type() == GuiType.ARENAS) {
                        String[] context = session.context().split(":", -1);
                        String kit = context.length > 1 ? context[1] : "";
                        int page = context.length > 2 ? Integer.parseInt(context[2]) : 0;
                        if (page > 0) openArenas(player, kit.isBlank() ? null : kit, page - 1);
                        else if (!kit.isBlank()) openKits(player);
                        else player.closeInventory();
                    } else if (session != null && session.type() == GuiType.PARTY_INVITES) openParty(player);
                    else player.closeInventory();
                }
                case "open_kits" -> openKits(player);
                case "open_arenas" -> openArenas(player, null);
                case "open_kit_editor" -> openKitEditor(player);
                case "open_spectator" -> openSpectators(player);
                case "open_party" -> openParty(player);
                case "open_stats" -> openStatistics(player);
                case "open_party_invites" -> openPartyInvites(player);
                case "party_create" -> partyResult(player, parties.create(player.getUniqueId()), "party-created");
                case "party_leave" -> partyResult(player, parties.leave(player.getUniqueId()), "party-left");
                case "party_chat" -> {
                    if (parties.find(player.getUniqueId()).isEmpty()) partyResult(player, PartyResult.fail("You are not in a party."), "");
                    else {
                        boolean enabled = parties.toggleChatState(player.getUniqueId());
                        player.sendMessage(messages.component(enabled ? "<green>Party chat enabled." : "<yellow>Party chat disabled."));
                    }
                }
                case "party_invite" -> {
                    try {
                        Player invited = Bukkit.getPlayer(UUID.fromString(target));
                        PartyResult result = invited == null ? PartyResult.fail("That player is no longer online.") : parties.invite(player.getUniqueId(), invited.getUniqueId());
                        if (result.success()) {
                            messages.send(player, "party-invite", Map.of("player", invited.getName()));
                            messages.send(invited, "party-invited", Map.of("player", player.getName()));
                        } else partyResult(player, result, "");
                    } catch (IllegalArgumentException ignored) { partyResult(player, PartyResult.fail("That player is unavailable."), ""); }
                }
                case "leave_ffa" -> respond(player, ffa.leave(player));
                case "edit_kit" -> respond(player, ffa.editKit(player, target));
                case "join_kit" -> {
                    if (config.runtime().selectionMode() == ArenaSelectionMode.GUI) openArenas(player, target);
                    else respond(player, ffa.joinKit(player, target));
                }
                case "select_arena" -> openKitsForArena(player, target);
                case "join_arena" -> {
                    String[] parts = target.split("\\|", 2);
                    respond(player, parts.length == 2 ? ffa.joinKitAt(player, parts[0], parts[1]) : ServiceResult.fail("arena-unavailable"));
                }
                case "arena_prev" -> {
                    GuiSession session = open.get(player.getUniqueId());
                    if (session != null) {
                        String[] context = session.context().split(":", -1);
                        String kit = context.length > 1 && !context[1].isBlank() ? context[1] : null;
                        openArenas(player, kit, Integer.parseInt(target));
                    }
                }
                case "arena_next" -> {
                    GuiSession session = open.get(player.getUniqueId());
                    if (session != null) {
                        String[] context = session.context().split(":", -1);
                        String kit = context.length > 1 && !context[1].isBlank() ? context[1] : null;
                        openArenas(player, kit, Integer.parseInt(target));
                    }
                }
                case "spectate" -> {
                    try {
                        Player targetPlayer = Bukkit.getPlayer(UUID.fromString(target));
                        respond(player, targetPlayer == null ? ServiceResult.fail("spectate-unavailable") : ffa.startSpectating(player, targetPlayer));
                    } catch (IllegalArgumentException ignored) { respond(player, ServiceResult.fail("spectate-unavailable")); }
                }
                default -> plugin.getLogger().warning("Ignored unregistered GUI action: " + action);
            }
        } finally { actionLocks.remove(player.getUniqueId()); }
    }

    public boolean owns(Inventory inventory) { return inventory.getHolder(false) instanceof GuiHolder; }

    public void closed(Player player, Inventory inventory) {
        GuiSession session = open.get(player.getUniqueId());
        if (inventory.getHolder(false) instanceof GuiHolder holder && session != null && session.type() == holder.type()) open.remove(player.getUniqueId());
    }

    private Inventory inventory(GuiType type, String menu) {
        ConfigurationSection section = definitions.getConfigurationSection("menus." + menu);
        int rows = section.getInt("rows");
        GuiHolder holder = new GuiHolder(type);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, messages.component(section.getString("title", "OrionFFA")));
        holder.inventory(inventory);
        return inventory;
    }

    private static int nextFreeSlot(Inventory inventory, int from) {
        for (int slot = Math.max(0, from); slot < inventory.getSize(); slot++) if (inventory.getItem(slot) == null) return slot;
        return -1;
    }

    private void addConfigured(Inventory inventory, String menu, String id, GuiType type, String action, String target) {
        ConfigurationSection item = definitions.getConfigurationSection("menus." + menu + ".items." + id);
        if (item == null) return;
        int slot = item.getInt("slot", -1);
        if (slot >= 0 && slot < inventory.getSize() && inventory.getItem(slot) == null) inventory.setItem(slot, configuredItem(item, type, action, target));
    }

    private ItemStack configuredItem(ConfigurationSection section, GuiType type, String action, String target) {
        Material material = Material.matchMaterial(section.getString("material", "BARRIER"));
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(section.getString("name", "<red>Invalid item")));
        meta.lore(section.getStringList("lore").stream().map(messages::component).toList());
        mark(meta.getPersistentDataContainer(), type, action, target);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, GuiType type, String action, String target) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(name));
        meta.lore(lore.stream().map(messages::component).toList());
        mark(meta.getPersistentDataContainer(), type, action, target);
        item.setItemMeta(meta);
        return item;
    }

    private void mark(PersistentDataContainer data, GuiType type, String action, String target) {
        data.set(guiKey, PersistentDataType.STRING, type.name());
        data.set(actionKey, PersistentDataType.STRING, action);
        data.set(targetKey, PersistentDataType.STRING, target);
    }

    private void show(Player player, Inventory inventory, GuiType type) { show(player, inventory, type, ""); }
    private void show(Player player, Inventory inventory, GuiType type, String context) {
        open.put(player.getUniqueId(), new GuiSession(type, context));
        player.openInventory(inventory);
    }

    private boolean locked(UUID playerId) {
        long now = System.currentTimeMillis();
        Long until = actionLocks.get(playerId);
        if (until != null && until > now) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) messages.send(player, "action-busy");
            return true;
        }
        actionLocks.put(playerId, now + 750);
        return false;
    }

    private void respond(Player player, ServiceResult result) { messages.send(player, result.messageKey(), result.placeholders()); }

    private static void validate(YamlConfiguration config) {
        java.util.Set<String> actions = java.util.Set.of("none", "open_kits", "open_arenas", "open_kit_editor", "open_spectator", "open_party", "open_stats", "open_party_invites", "party_create", "party_leave", "party_chat", "party_invite", "leave_ffa", "join_kit", "join_arena", "select_arena", "arena_prev", "arena_next", "spectate", "back");
        for (String menu : List.of("kit_selector", "kit_editor_selector", "arena_selector", "spectator_selector", "party", "party_invites", "statistics")) {
            ConfigurationSection section = config.getConfigurationSection("menus." + menu);
            if (section == null) throw new IllegalArgumentException("menus." + menu + " is required");
            int rows = section.getInt("rows");
            if (rows < 1 || rows > 6) throw new IllegalArgumentException("menus." + menu + ".rows must be between 1 and 6");
            ConfigurationSection items = section.getConfigurationSection("items");
            if (items == null) continue;
            java.util.Set<Integer> usedSlots = new java.util.HashSet<>();
            for (String item : items.getKeys(false)) {
                int slot = items.getInt(item + ".slot", -1);
                if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("menus." + menu + ".items." + item + ".slot is outside the menu");
                if (!usedSlots.add(slot)) throw new IllegalArgumentException("menus." + menu + " contains multiple items in slot " + slot);
                String action = items.getString(item + ".action", "");
                if (action.isBlank()) throw new IllegalArgumentException("menus." + menu + ".items." + item + ".action is required");
                if (!actions.contains(action)) throw new IllegalArgumentException("menus." + menu + ".items." + item + ".action is not registered: " + action);
            }
        }
    }

    private record GuiSession(GuiType type, String context) { }
    private String playerName(UUID playerId) { Player player = Bukkit.getPlayer(playerId); return player == null ? "Offline" : player.getName(); }
    private void partyResult(Player player, PartyResult result, String successKey) { if (result.success()) messages.send(player, successKey); else messages.send(player, "party-error", Map.of("reason", result.reason())); }
}
