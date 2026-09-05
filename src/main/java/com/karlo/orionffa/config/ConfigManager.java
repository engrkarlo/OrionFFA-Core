package com.karlo.orionffa.config;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Objects;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private RuntimeConfig runtime;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        RuntimeConfig candidate = new RuntimeConfig(
                config.getBoolean("ffa.enabled", true),
                location(config, "lobby"),
                location(config, "edit-kit"),
                Duration.ofSeconds(Math.max(1, config.getLong("combat.tag-seconds", 10))),
                Duration.ofSeconds(Math.max(1, config.getLong("combat.killer-credit-seconds", 15))),
                Math.max(2, config.getInt("party.max-size", 10)),
                Duration.ofSeconds(Math.max(1, config.getLong("party.invite-seconds", 60))),
                ArenaSelectionMode.parse(config.getString("arena-selection.mode", "automatic")),
                ArenaSelectionStrategy.parse(config.getString("arena-selection.strategy", "least-occupied")),
                config.getBoolean("plugin.debug", false));
        runtime = candidate;
    }

    public RuntimeConfig runtime() {
        return runtime;
    }

    public FileConfiguration file() {
        return plugin.getConfig();
    }

    public void save() {
        plugin.saveConfig();
    }

    public void setLobby(Location location) {
        setLocation("lobby", location);
        reload();
    }

    public void setEditKit(Location location) {
        setLocation("edit-kit", location);
        reload();
    }

    private void setLocation(String path, Location location) {
        Objects.requireNonNull(location.getWorld(), "location world");
        plugin.getConfig().set(path, null);
        ConfigurationSection section = plugin.getConfig().createSection(path);
        new LocationConfig(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())
                .write(section);
        plugin.saveConfig();
    }

    private static LocationConfig location(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalStateException("Missing required configuration section: " + path);
        }
        return LocationConfig.from(section);
    }
}
