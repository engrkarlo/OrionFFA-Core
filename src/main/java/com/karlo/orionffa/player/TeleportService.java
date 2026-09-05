package com.karlo.orionffa.player;

import com.karlo.orionffa.config.LocationConfig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Optional;

public final class TeleportService {
    public boolean teleport(Player player, LocationConfig destination) {
        Optional<Location> resolved = destination.resolve();
        return resolved.filter(location -> teleport(player, location)).isPresent();
    }

    public boolean teleport(Player player, Location destination) {
        prepare(player);
        if (!player.teleport(destination)) return false;
        normalize(player);
        return true;
    }

    public void prepare(Player player) {
        player.closeInventory();
        if (player.isInsideVehicle()) player.leaveVehicle();
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        player.setGliding(false);
        player.setSwimming(false);
        player.setSprinting(false);
        player.setSneaking(false);
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
    }

    public void normalize(Player player) {
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        player.setGliding(false);
        player.setSwimming(false);
        player.setSprinting(false);
        player.setSneaking(false);
        player.setAllowFlight(false);
        player.setFlying(false);
    }
}
