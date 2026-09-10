package com.karlo.orionffa.arena;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SchematicService {
    void paste(File schematic, Location target) throws Exception;

    default boolean asyncCapable() {
        return false;
    }

    default boolean giveSelectionWand(Player player) {
        return false;
    }

    /**
     * Releases any temporary provider permission granted for arena selection.
     * Implementations that do not grant temporary permission may ignore this.
     */
    default void releaseSelectionWand(Player player) {
    }

    default Optional<ArenaSelection> captureSelection(Player player) {
        return Optional.empty();
    }

    default CompletableFuture<Void> saveSelection(Player player, File schematic) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Selection saving is unavailable"));
    }
}
