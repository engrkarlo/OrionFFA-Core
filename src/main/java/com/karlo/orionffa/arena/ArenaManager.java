package com.karlo.orionffa.arena;

import com.karlo.orionffa.config.ArenaSelectionStrategy;
import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.config.LocationConfig;
import com.karlo.orionffa.kit.KitManager;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

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

public final class ArenaManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final KitManager kits;
    private Map<String, Arena> arenas = Map.of();

    public ArenaManager(JavaPlugin plugin, ConfigManager config, KitManager kits) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
        reload();
    }

    public void reload() {
        ConfigurationSection root = config.file().getConfigurationSection("arenas");
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
            LocationConfig splitA = section.getConfigurationSection("split-spawns.team-a") == null ? null : LocationConfig.from(section.getConfigurationSection("split-spawns.team-a"));
            LocationConfig splitB = section.getConfigurationSection("split-spawns.team-b") == null ? null : LocationConfig.from(section.getConfigurationSection("split-spawns.team-b"));
            loaded.put(id, new Arena(id, LocationConfig.from(section.getConfigurationSection("spawn")),
                    section.getBoolean("enabled", true), Math.max(1, section.getInt("capacity", 1)), boundKit, allowed,
                    section.getBoolean("shared", false), splitA, splitB));
        }
        arenas = Map.copyOf(loaded);
    }

    public Optional<Arena> get(String id) {
        return Optional.ofNullable(arenas.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<String> names() {
        return arenas.keySet().stream().sorted().toList();
    }

    public List<Arena> availableFor(String kitId) {
        return arenas.values().stream().filter(arena -> isAvailable(arena, kitId)).toList();
    }

    public Optional<Arena> select(String kitId, ArenaSelectionStrategy strategy) {
        List<Arena> candidates = new ArrayList<>(availableFor(kitId));
        if (candidates.isEmpty()) return Optional.empty();
        if (strategy == ArenaSelectionStrategy.RANDOM) {
            java.util.Collections.shuffle(candidates);
        } else if (strategy == ArenaSelectionStrategy.LEAST_OCCUPIED) {
            candidates.sort(Comparator.comparingInt(Arena::occupants).thenComparing(Arena::id));
        } else {
            candidates.sort(Comparator.comparing(Arena::id));
        }
        return Optional.of(candidates.getFirst());
    }

    public boolean reserve(Arena arena, UUID playerId) {
        return isAvailable(arena, null) && arena.reserve(playerId, Instant.now().plusSeconds(10));
    }

    public void release(Arena arena, UUID playerId) { arena.release(playerId); }
    public void claim(Arena arena, UUID playerId) { arena.claim(playerId); }
    public void leave(String arenaId, UUID playerId) { get(arenaId).ifPresent(arena -> arena.leave(playerId)); }

    public boolean save(String rawId, Location location) {
        String id = validId(rawId);
        if (id == null || location.getWorld() == null) return false;
        ConfigurationSection section = config.file().getConfigurationSection("arenas." + id);
        boolean created = section == null;
        if (created) section = config.file().createSection("arenas." + id);
        section.set("enabled", true);
        section.set("world", location.getWorld().getName());
        section.set("spawn", null);
        new LocationConfig(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())
                .write(section.createSection("spawn"));
        if (created) {
            section.set("capacity", 40);
            section.set("bound-kit", null);
        }
        config.save();
        reload();
        return true;
    }

    public boolean bind(String arenaId, String kitId) {
        String kit = kitId.toLowerCase(Locale.ROOT);
        if (!get(arenaId).isPresent() || !kits.existsAndEnabled(kit)) return false;
        config.file().set("arenas." + arenaId.toLowerCase(Locale.ROOT) + ".bound-kit", kit);
        config.save();
        reload();
        return true;
    }

    public boolean delete(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        Arena existing = arenas.get(id);
        if (existing == null || existing.occupants() > 0) return false;
        config.file().set("arenas." + id, null);
        ConfigurationSection kitRoot = config.file().getConfigurationSection("kits");
        if (kitRoot != null) for (String kitId : kitRoot.getKeys(false)) {
            if (id.equalsIgnoreCase(kitRoot.getString(kitId + ".arena"))) kitRoot.set(kitId + ".arena", "");
        }
        config.save();
        reload();
        return true;
    }

    private boolean isAvailable(Arena arena, String kitId) {
        return arena.enabled() && arena.spawn().resolve().isPresent()
                && (kitId == null || arena.supports(kitId)) && arena.occupants() < arena.capacity();
    }

    private static String normalizedKit(String kit) {
        return kit == null || kit.isBlank() ? null : kit.toLowerCase(Locale.ROOT);
    }

    private static String validId(String raw) {
        return raw.matches("[a-zA-Z0-9_-]{1,32}") ? raw.toLowerCase(Locale.ROOT) : null;
    }
}
