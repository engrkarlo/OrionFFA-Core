package com.karlo.orionffa.arena;

import com.karlo.orionffa.config.ArenaSelectionStrategy;
import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.config.LocationConfig;
import com.karlo.orionffa.kit.KitManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ArenaManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final KitManager kits;
    private final SchematicService schematicService;
    private final File file;
    private final NamespacedKey selectionCancelKey;
    private final Set<UUID> suspendedLobbyMenus = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeSelections = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private YamlConfiguration arenaConfig;
    private Map<String, Arena> arenas = Map.of();
    private Consumer<Player> lobbyMenuApplier = player -> { };

    public ArenaManager(JavaPlugin plugin, ConfigManager config, KitManager kits, SchematicService schematicService) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
        this.schematicService = schematicService;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        this.selectionCancelKey = new NamespacedKey(plugin, "arena-selection-cancel");
        ensureFileAndMigrateLegacyArenas();
        reload();
    }

    public void setLobbyMenuApplier(Consumer<Player> lobbyMenuApplier) {
        this.lobbyMenuApplier = lobbyMenuApplier == null ? player -> { } : lobbyMenuApplier;
    }

    public void reload() {
        arenaConfig = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = arenaConfig.getConfigurationSection("arenas");
        Map<String, Arena> loaded = new LinkedHashMap<>();
        if (root != null) for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null || section.getConfigurationSection("spawn") == null) continue;
            String id = rawId.toLowerCase(Locale.ROOT);
            String boundKit = normalizedKit(section.getString("bound-kit"));
            if (boundKit != null && !kits.existsAndEnabled(boundKit)) {
                plugin.getLogger().warning("Arena " + id + " has an unavailable bound kit: " + boundKit);
                boundKit = null;
            }
            Set<String> allowed = section.getStringList("allowed-kits").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).filter(kits::existsAndEnabled).collect(java.util.stream.Collectors.toUnmodifiableSet());
            ConfigurationSection reset = section.getConfigurationSection("reset");
            LocationConfig resetOrigin = reset == null || reset.getConfigurationSection("origin") == null
                    ? LocationConfig.from(section.getConfigurationSection("spawn")) : LocationConfig.from(reset.getConfigurationSection("origin"));
            ArenaSelection selection = readSelection(reset);
            String schematic = reset == null ? "schematics/" + id + ".schem" : reset.getString("schematic", "schematics/" + id + ".schem");
            LocationConfig splitA = section.getConfigurationSection("split-spawns.team-a") == null ? null : LocationConfig.from(section.getConfigurationSection("split-spawns.team-a"));
            LocationConfig splitB = section.getConfigurationSection("split-spawns.team-b") == null ? null : LocationConfig.from(section.getConfigurationSection("split-spawns.team-b"));
            loaded.put(id, new Arena(id, LocationConfig.from(section.getConfigurationSection("spawn")), resetOrigin, selection,
                    section.getBoolean("enabled", true), Math.max(1, section.getInt("capacity", 40)), boundKit, allowed,
                    section.getBoolean("shared", false), section.getBoolean("locked", false), splitA, splitB, schematic));
        }
        arenas = Map.copyOf(loaded);
    }

    public Optional<Arena> get(String id) { return Optional.ofNullable(arenas.get(id.toLowerCase(Locale.ROOT))); }
    public List<String> names() { return arenas.keySet().stream().sorted().toList(); }
    public List<Arena> all() { return arenas.values().stream().sorted(Comparator.comparing(Arena::id)).toList(); }
    public List<Arena> availableFor(String kitId) { return arenas.values().stream().filter(arena -> isAvailable(arena, kitId)).toList(); }

    public Optional<Arena> select(String kitId, ArenaSelectionStrategy strategy) {
        List<Arena> candidates = new ArrayList<>(availableFor(kitId));
        if (candidates.isEmpty()) return Optional.empty();
        if (strategy == ArenaSelectionStrategy.RANDOM) java.util.Collections.shuffle(candidates);
        else if (strategy == ArenaSelectionStrategy.LEAST_OCCUPIED) candidates.sort(Comparator.comparingInt(Arena::occupants).thenComparing(Arena::id));
        else candidates.sort(Comparator.comparing(Arena::id));
        return Optional.of(candidates.getFirst());
    }

    public boolean giveSelectionWand(Player player) {
        if (schematicService == null) return false;
        cancelSelection(player);
        suspendLobbyMenu(player);
        if (!schematicService.giveSelectionWand(player)) {
            restoreLobbyMenu(player);
            return false;
        }
        if (!giveSelectionCancelItem(player)) {
            schematicService.releaseSelectionWand(player);
            restoreLobbyMenu(player);
            return false;
        }
        activeSelections.add(player.getUniqueId());
        return true;
    }

    public boolean isSelectionCancelItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(selectionCancelKey, PersistentDataType.BYTE);
    }

    public boolean isSelectionActive(Player player) {
        return activeSelections.contains(player.getUniqueId());
    }

    public void cancelSelection(Player player) {
        boolean active = activeSelections.remove(player.getUniqueId())
                || java.util.Arrays.stream(player.getInventory().getContents()).anyMatch(this::isSelectionCancelItem);
        removeSelectionCancelItems(player);
        if (schematicService != null) schematicService.releaseSelectionWand(player);
        if (active || suspendedLobbyMenus.contains(player.getUniqueId())) restoreLobbyMenu(player);
    }

    private boolean giveSelectionCancelItem(Player player) {
        removeSelectionCancelItems(player);
        ConfigurationSection section = config.file().getConfigurationSection("arena-selection.cancel-item");
        Material material = Material.matchMaterial(section == null ? "BARRIER" : section.getString("material", "BARRIER"));
        if (material == null || material.isAir()) material = Material.BARRIER;
        int configuredSlot = section == null ? 8 : Math.max(0, Math.min(8, section.getInt("slot", 8)));
        int slot = configuredSlot;
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing != null && !existing.getType().isAir()) {
            slot = -1;
            for (int candidate = 0; candidate < 9; candidate++) {
                ItemStack candidateItem = player.getInventory().getItem(candidate);
                if (candidateItem == null || candidateItem.getType().isAir()) { slot = candidate; break; }
            }
        }
        if (slot < 0) return false;
        ItemStack item = new ItemStack(material, Math.max(1, section == null ? 1 : section.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        String name = section == null ? "<red>Cancel Arena Selection</red>" : section.getString("name", "<red>Cancel Arena Selection</red>");
        meta.displayName(MiniMessage.miniMessage().deserialize(name));
        if (section != null) meta.lore(section.getStringList("lore").stream().map(value -> MiniMessage.miniMessage().deserialize(value)).toList());
        meta.getPersistentDataContainer().set(selectionCancelKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);
        return true;
    }

    private void removeSelectionCancelItems(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isSelectionCancelItem(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        }
    }

    public boolean reserve(Arena arena, UUID playerId) { return isAvailable(arena, null) && arena.reserve(playerId, Instant.now().plusSeconds(10)); }
    public void release(Arena arena, UUID playerId) { arena.release(playerId); }
    public void claim(Arena arena, UUID playerId) { arena.claim(playerId); }
    public void leave(String arenaId, UUID playerId) { get(arenaId).ifPresent(arena -> arena.leave(playerId)); }

    public CompletableFuture<ArenaSaveResult> save(Player player, String rawId) {
        String id = validId(rawId);
        if (id == null) return CompletableFuture.completedFuture(ArenaSaveResult.failure("arena-name-invalid"));
        if (!activeSelections.contains(player.getUniqueId())) return CompletableFuture.completedFuture(ArenaSaveResult.failure("arena-selection-inactive"));
        if (schematicService == null) return CompletableFuture.completedFuture(ArenaSaveResult.failure("reset-adapter-missing"));
        Optional<ArenaSelection> selection = schematicService.captureSelection(player);
        if (selection.isEmpty()) return CompletableFuture.completedFuture(ArenaSaveResult.failure("arena-selection-missing"));
        if (!selection.get().world().equals(player.getWorld().getName())) return CompletableFuture.completedFuture(ArenaSaveResult.failure("arena-selection-world"));
        // The creator is expected to stand at the desired arena spawn when saving. That
        // location may legitimately be inside the selected cuboid, so do not treat the
        // current player's presence inside the selection as arena occupancy.
        if (get(id).map(Arena::occupants).orElse(0) > 0) {
            return CompletableFuture.completedFuture(ArenaSaveResult.failure("arena-occupied"));
        }
        File schematic = new File(plugin.getDataFolder(), "schematics/" + id + ".schem");
        Location spawn = player.getLocation().clone();
        ArenaSelection bounds = selection.get();
        CompletableFuture<ArenaSaveResult> result = new CompletableFuture<>();
        schematicService.saveSelection(player, schematic).whenComplete((ignored, failure) -> {
            if (failure != null) {
                result.complete(ArenaSaveResult.failure("arena-save-failed"));
                return;
            }
            YamlConfiguration snapshot = buildSavedConfig(id, spawn, bounds);
            String serialized = snapshot.saveToString();
            CompletableFuture.runAsync(() -> {
                try { Files.writeString(file.toPath(), serialized, StandardCharsets.UTF_8); }
                catch (IOException exception) { throw new RuntimeException(exception); }
            }).whenComplete((written, writeFailure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (writeFailure != null) result.complete(ArenaSaveResult.failure("arena-save-failed"));
                else {
                    activeSelections.remove(player.getUniqueId());
                    reload();
                    restoreLobbyMenu(player);
                    result.complete(ArenaSaveResult.success(id));
                }
            }));
        });
        return result;
    }

    private void suspendLobbyMenu(Player player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || !item.hasItemMeta()) continue;
            org.bukkit.persistence.PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "gui");
            if ("LOBBY".equals(data.get(key, PersistentDataType.STRING))) player.getInventory().setItem(slot, null);
        }
        suspendedLobbyMenus.add(player.getUniqueId());
    }

    private void restoreLobbyMenu(Player player) {
        if (!suspendedLobbyMenus.remove(player.getUniqueId())) return;
        lobbyMenuApplier.accept(player);
    }

    public boolean bind(String arenaId, String kitId) {
        String kit = kitId.toLowerCase(Locale.ROOT);
        if (get(arenaId).isEmpty() || !kits.existsAndEnabled(kit)) return false;
        arenaConfig.set("arenas." + arenaId.toLowerCase(Locale.ROOT) + ".bound-kit", kit);
        persistSnapshot(); reload(); return true;
    }

    public boolean setLocked(String rawId, boolean locked) {
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!arenas.containsKey(id)) return false;
        arenaConfig.set("arenas." + id + ".locked", locked); persistSnapshot(); reload(); return true;
    }

    public boolean setSpawn(String rawId, Location location) {
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!arenas.containsKey(id) || location.getWorld() == null || hasPlayersInside(arenas.get(id))) return false;
        arenaConfig.set("arenas." + id + ".spawn", null);
        new LocationConfig(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()).write(arenaConfig.createSection("arenas." + id + ".spawn"));
        persistSnapshot(); reload(); return true;
    }

    public boolean setCapacity(String rawId, int capacity) {
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!arenas.containsKey(id) || capacity < 1 || capacity < arenas.get(id).occupants()) return false;
        arenaConfig.set("arenas." + id + ".capacity", capacity); persistSnapshot(); reload(); return true;
    }

    public boolean delete(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        Arena existing = arenas.get(id);
        if (existing == null || existing.occupants() > 0 || hasPlayersInside(existing)) return false;
        try { Files.deleteIfExists(schematicFile(existing).toPath()); }
        catch (IOException exception) { plugin.getLogger().warning("Could not delete schematic for arena " + id + ": " + exception.getMessage()); }
        arenaConfig.set("arenas." + id, null);
        ConfigurationSection kitRoot = config.file().getConfigurationSection("kits");
        if (kitRoot != null) for (String kitId : kitRoot.getKeys(false)) if (id.equalsIgnoreCase(kitRoot.getString(kitId + ".arena"))) kitRoot.set(kitId + ".arena", "");
        persistSnapshot(); reload(); return true;
    }

    public boolean hasPlayersInside(Arena arena) {
        if (arena == null || arena.occupants() > 0) return arena != null && arena.occupants() > 0;
        return Bukkit.getOnlinePlayers().stream().anyMatch(player -> arena.contains(player.getLocation()));
    }
    public boolean hasPlayersInside(ArenaSelection selection) { return Bukkit.getOnlinePlayers().stream().anyMatch(player -> selection.contains(player.getLocation())); }
    public File schematicFile(Arena arena) { return new File(plugin.getDataFolder(), arena.schematicPath()); }
    public Optional<Location> resetTarget(Arena arena) { return arena.resetOrigin().resolve(); }

    private boolean isAvailable(Arena arena, String kitId) {
        return arena.enabled() && !arena.locked() && arena.spawn().resolve().isPresent() && (kitId == null || arena.supports(kitId)) && arena.occupants() < arena.capacity();
    }

    private YamlConfiguration buildSavedConfig(String id, Location spawn, ArenaSelection selection) {
        YamlConfiguration snapshot = new YamlConfiguration();
        ConfigurationSection existing = arenaConfig.getConfigurationSection("arenas");
        if (existing != null) for (Map.Entry<String, Object> entry : existing.getValues(false).entrySet()) snapshot.set("arenas." + entry.getKey(), entry.getValue());
        Arena old = get(id).orElse(null);
        snapshot.set("arenas." + id + ".enabled", true);
        snapshot.set("arenas." + id + ".locked", false);
        snapshot.set("arenas." + id + ".capacity", old == null ? 40 : old.capacity());
        snapshot.set("arenas." + id + ".bound-kit", old == null ? null : old.boundKit());
        snapshot.set("arenas." + id + ".allowed-kits", old == null ? List.of() : old.allowedKits().stream().sorted().toList());
        snapshot.set("arenas." + id + ".shared", old != null && old.shared());
        snapshot.set("arenas." + id + ".spawn", null);
        new LocationConfig(spawn.getWorld().getName(), spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()).write(snapshot.createSection("arenas." + id + ".spawn"));
        snapshot.set("arenas." + id + ".reset.schematic", "schematics/" + id + ".schem");
        new LocationConfig(selection.world(), selection.minX(), selection.minY(), selection.minZ(), 0, 0).write(snapshot.createSection("arenas." + id + ".reset.origin"));
        snapshot.set("arenas." + id + ".reset.min.x", selection.minX());
        snapshot.set("arenas." + id + ".reset.min.y", selection.minY());
        snapshot.set("arenas." + id + ".reset.min.z", selection.minZ());
        snapshot.set("arenas." + id + ".reset.max.x", selection.maxX());
        snapshot.set("arenas." + id + ".reset.max.y", selection.maxY());
        snapshot.set("arenas." + id + ".reset.max.z", selection.maxZ());
        return snapshot;
    }

    private ArenaSelection readSelection(ConfigurationSection reset) {
        if (reset == null || reset.getConfigurationSection("min") == null || reset.getConfigurationSection("max") == null || reset.getConfigurationSection("origin") == null) return null;
        ConfigurationSection min = reset.getConfigurationSection("min");
        ConfigurationSection max = reset.getConfigurationSection("max");
        LocationConfig origin = LocationConfig.from(reset.getConfigurationSection("origin"));
        return new ArenaSelection(origin.world(), min.getInt("x"), min.getInt("y"), min.getInt("z"), max.getInt("x"), max.getInt("y"), max.getInt("z"));
    }

    private void ensureFileAndMigrateLegacyArenas() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { Files.writeString(file.toPath(), "# OrionFFA arena definitions. Managed by /offa arena commands.\narenas: {}\n", StandardCharsets.UTF_8); }
            catch (IOException exception) { throw new IllegalStateException("Could not create arenas.yml", exception); }
        }
        ConfigurationSection legacy = config.file().getConfigurationSection("arenas");
        if (legacy == null) return;
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        if (migrated.getConfigurationSection("arenas") == null || migrated.getConfigurationSection("arenas").getKeys(false).isEmpty()) {
            migrated.set("arenas", legacy.getValues(false));
            ConfigurationSection migratedRoot = migrated.getConfigurationSection("arenas");
            if (migratedRoot != null) for (String id : migratedRoot.getKeys(false)) {
                String oldSchematic = config.file().getString("arena-reset.arenas." + id + ".schematic", "");
                if (!oldSchematic.isBlank()) migrated.set("arenas." + id + ".reset.schematic", oldSchematic);
            }
            try { migrated.save(file); }
            catch (IOException exception) { throw new IllegalStateException("Could not migrate arenas.yml", exception); }
        }
        config.file().set("arenas", null); config.file().set("arena-reset.arenas", null); config.save();
    }

    private void persistSnapshot() {
        try { arenaConfig.save(file); }
        catch (IOException exception) { plugin.getLogger().warning("Could not save arenas.yml: " + exception.getMessage()); }
    }

    private static String normalizedKit(String kit) { return kit == null || kit.isBlank() ? null : kit.toLowerCase(Locale.ROOT); }
    private static String validId(String raw) { return raw != null && raw.matches("[a-zA-Z0-9_-]{1,32}") ? raw.toLowerCase(Locale.ROOT) : null; }

    public record ArenaSaveResult(boolean success, String messageKey, String arenaId) {
        static ArenaSaveResult success(String id) { return new ArenaSaveResult(true, "arena-saved", id); }
        static ArenaSaveResult failure(String key) { return new ArenaSaveResult(false, key, null); }
    }
}
