package com.karlo.orionffa.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Optional;

public record LocationConfig(String world, double x, double y, double z, float yaw, float pitch) {
    public static LocationConfig from(ConfigurationSection section) {
        return new LocationConfig(
                section.getString("world", ""), section.getDouble("x"), section.getDouble("y"),
                section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public Optional<Location> resolve() {
        World resolved = Bukkit.getWorld(world);
        return resolved == null ? Optional.empty() : Optional.of(new Location(resolved, x, y, z, yaw, pitch));
    }

    public void write(ConfigurationSection section) {
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("pitch", pitch);
    }
}
