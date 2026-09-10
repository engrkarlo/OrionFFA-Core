package com.karlo.orionffa.arena;

import org.bukkit.Location;
import org.bukkit.World;

/** Immutable reset/occupancy bounds captured from a WorldEdit/FAWE cuboid selection. */
public record ArenaSelection(
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public boolean contains(Location location) {
        World locationWorld = location.getWorld();
        if (locationWorld == null || !locationWorld.getName().equals(world)) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public Location origin(World resolvedWorld) {
        return new Location(resolvedWorld, minX, minY, minZ);
    }
}
