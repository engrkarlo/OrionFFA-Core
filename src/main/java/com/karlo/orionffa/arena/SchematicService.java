package com.karlo.orionffa.arena;

import org.bukkit.Location;

import java.io.File;

@FunctionalInterface
public interface SchematicService {
    void paste(File schematic, Location target) throws Exception;

    default boolean asyncCapable() {
        return false;
    }
}
