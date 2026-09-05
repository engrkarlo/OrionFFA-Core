package com.karlo.orionffa.ffa;

import com.karlo.orionffa.arena.Arena;
import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.arena.ArenaResetService;
import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.kit.KitDefinition;
import com.karlo.orionffa.kit.KitManager;
import com.karlo.orionffa.kit.KitPersistenceManager;
import com.karlo.orionffa.storage.PlayerKitData;
import com.karlo.orionffa.player.FfaState;
import com.karlo.orionffa.player.PlayerSession;
import com.karlo.orionffa.player.PlayerSessionManager;
import com.karlo.orionffa.player.PlayerSnapshot;
import com.karlo.orionffa.player.TeleportService;
import org.bukkit.GameMode;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FfaService {
    private final ConfigManager config;
    private final PlayerSessionManager sessions;
    private final KitManager kits;
    private final ArenaManager arenas;
    private final TeleportService teleports;
    private final KitPersistenceManager customKits;
    private final ArenaResetService resets;

    public FfaService(ConfigManager config, PlayerSessionManager sessions, KitManager kits, ArenaManager arenas, TeleportService teleports, KitPersistenceManager customKits, ArenaResetService resets) {
        this.config = config;
        this.sessions = sessions;
        this.kits = kits;
        this.arenas = arenas;
        this.teleports = teleports;
        this.customKits = customKits;
        this.resets = resets;
    }

    public ServiceResult enterLobby(Player player) {
        if (!config.runtime().ffaEnabled()) return ServiceResult.fail("ffa-disabled");
        Optional<PlayerSession> existing=sessions.get(player.getUniqueId());
        if(existing.isPresent()){
            if(existing.get().state()==FfaState.FFA)return ServiceResult.ok("already-in-ffa");
            if(existing.get().state()==FfaState.SPECTATING)return ServiceResult.fail("spectate-unavailable");
            if(existing.get().state()==FfaState.EDITING_KIT)return ServiceResult.fail("already-editing-kit");
        }
        PlayerSession session = sessions.enter(player);
        if (!teleports.teleport(player, config.runtime().lobby())) {
            sessions.remove(player.getUniqueId());
            return ServiceResult.fail("world-unavailable");
        }
        session.state(FfaState.FFA);
        return ServiceResult.ok("entered-lobby");
    }

    public ServiceResult editKit(Player player, String kitId) {
        if (!config.runtime().ffaEnabled()) return ServiceResult.fail("ffa-disabled");
        Optional<KitDefinition> kit = kits.find(kitId);
        if (kit.isEmpty()) return ServiceResult.fail("kit-unavailable");
        PlayerSession session = sessions.enter(player);
        FfaState previousState = session.state();
        String previousArena = session.arenaId();
        if (!teleports.teleport(player, config.runtime().editKit())) {
            if (previousState == FfaState.OUTSIDE) sessions.remove(player.getUniqueId());
            return ServiceResult.fail("world-unavailable");
        }
        if (previousArena != null) arenas.leave(previousArena, player.getUniqueId());
        applyKit(player, kit.get(), customKits.find(player.getUniqueId(), kit.get().id()).orElse(null));
        session.kitId(kit.get().id());
        session.arenaId(null);
        session.spectatorTarget(null);
        session.state(FfaState.EDITING_KIT);
        return ServiceResult.ok("kit-editing", Map.of("kit", kit.get().id()));
    }

    public ServiceResult saveEditedKit(Player player, String kitId) {
        Optional<PlayerSession> session = sessions.get(player.getUniqueId());
        if (session.isEmpty() || session.get().state() != FfaState.EDITING_KIT) return ServiceResult.fail("not-editing-kit");
        if (kitId == null || !kitId.equalsIgnoreCase(session.get().kitId())) return ServiceResult.fail("kit-unavailable");
        customKits.saveFromPlayer(player, session.get().kitId());
        return ServiceResult.ok("kit-saved", Map.of("kit", session.get().kitId()));
    }

    public ServiceResult joinKit(Player player, String kitId) {
        if (!config.runtime().ffaEnabled()) return ServiceResult.fail("ffa-disabled");
        Optional<KitDefinition> kit = kits.find(kitId);
        if (kit.isEmpty()) return ServiceResult.fail("kit-unavailable");
        Optional<Arena> target = arenas.select(kit.get().id(), config.runtime().selectionStrategy());
        return target.filter(arena -> !resets.isResetting(arena.id())).map(arena -> join(player, kit.get(), arena)).orElseGet(() -> ServiceResult.fail("arena-unavailable"));
    }

    public ServiceResult joinKitAt(Player player, String kitId, String arenaId) {
        if (!config.runtime().ffaEnabled()) return ServiceResult.fail("ffa-disabled");
        Optional<KitDefinition> kit = kits.find(kitId);
        Optional<Arena> arena = arenas.get(arenaId);
        if (kit.isEmpty()) return ServiceResult.fail("kit-unavailable");
        if (arena.isEmpty() || resets.isResetting(arena.get().id()) || !arenas.availableFor(kit.get().id()).contains(arena.get())) return ServiceResult.fail("arena-unavailable");
        return join(player, kit.get(), arena.get());
    }

    public List<Arena> availableArenas(String kitId) {
        return arenas.availableFor(kitId).stream().filter(a -> !resets.isResetting(a.id())).toList();
    }

    private ServiceResult join(Player player, KitDefinition kit, Arena arena) {
        PlayerSession session = sessions.enter(player);
        String previousKit = session.kitId();
        String previousArena = session.arenaId();
        FfaState previousState = session.state();
        UUID playerId = player.getUniqueId();

        if (!arenas.reserve(arena, playerId)) return ServiceResult.fail("arena-unavailable");
        try {
            // Claim the capacity before teleporting. If the teleport fails, the player has
            // not been moved and the reservation can be rolled back without changing the
            // authoritative session.
            if (!arena.claim(playerId)) return ServiceResult.fail("arena-unavailable");
            if (!teleports.teleport(player, arena.spawn())) {
                arenas.leave(arena.id(), playerId);
                return ServiceResult.fail("world-unavailable");
            }

            applyKit(player, kit, customKits.find(player.getUniqueId(), kit.id()).orElse(null));
            if (previousArena != null && !previousArena.equals(arena.id())) {
                arenas.leave(previousArena, playerId);
            }
            session.kitId(kit.id());
            session.arenaId(arena.id());
            session.spectatorTarget(null);
            session.state(FfaState.FFA);
            return ServiceResult.ok("joined-kit", Map.of("kit", kit.id(), "arena", arena.id()));
        } catch (RuntimeException failure) {
            arenas.leave(arena.id(), playerId);
            session.kitId(previousKit);
            session.arenaId(previousArena);
            session.spectatorTarget(null);
            session.state(previousState);
            if (previousState == FfaState.OUTSIDE) {
                restore(player, session.snapshot());
                sessions.remove(playerId);
            } else if (previousState == FfaState.FFA && previousKit != null && previousArena != null) {
                Optional<KitDefinition> oldKit = kits.find(previousKit);
                Optional<Arena> oldArena = arenas.get(previousArena);
                if (oldKit.isPresent() && oldArena.isPresent() && teleports.teleport(player, oldArena.get().spawn())) {
                    applyKit(player, oldKit.get(), customKits.find(playerId, oldKit.get().id()).orElse(null));
                } else {
                    restore(player, session.snapshot());
                }
            } else {
                restore(player, session.snapshot());
            }
            return ServiceResult.fail("action-failed");
        } finally {
            arenas.release(arena, playerId);
        }
    }

    public ServiceResult leave(Player player) {
        Optional<PlayerSession> found = sessions.get(player.getUniqueId());
        if (found.isEmpty()) return ServiceResult.ok("left-ffa");
        PlayerSession session = found.get();
        PlayerSnapshot snapshot = session.snapshot();
        if (!teleports.teleport(player, snapshot.location())) return ServiceResult.fail("world-unavailable");
        restore(player, snapshot);
        if (session.arenaId() != null) arenas.leave(session.arenaId(), player.getUniqueId());
        sessions.remove(player.getUniqueId());
        return ServiceResult.ok("left-ffa");
    }

    public ServiceResult startSpectating(Player spectator, Player target) {
        if (spectator.equals(target)) return ServiceResult.fail("spectate-self");
        Optional<PlayerSession> targetSession = sessions.get(target.getUniqueId());
        if (targetSession.isEmpty() || targetSession.get().state() != FfaState.FFA) return ServiceResult.fail("spectate-unavailable");
        PlayerSession session = sessions.enter(spectator);
        if (session.arenaId() != null) arenas.leave(session.arenaId(), spectator.getUniqueId());
        teleports.prepare(spectator);
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setSpectatorTarget(target);
        session.spectatorTarget(target.getUniqueId());
        session.state(FfaState.SPECTATING);
        return ServiceResult.ok("spectating", Map.of("player", target.getName()));
    }

    public ServiceResult stopSpectating(Player player) {
        Optional<PlayerSession> found = sessions.get(player.getUniqueId());
        if (found.isEmpty() || found.get().state() != FfaState.SPECTATING) return ServiceResult.fail("spectate-unavailable");
        PlayerSession session = found.get();
        player.setSpectatorTarget(null);
        if (session.kitId() == null || session.arenaId() == null) return leave(player);
        Optional<KitDefinition> kit = kits.find(session.kitId());
        Optional<Arena> arena = arenas.get(session.arenaId());
        if (kit.isEmpty() || arena.isEmpty() || !arenas.reserve(arena.get(), player.getUniqueId())) return leave(player);
        try {
            UUID playerId = player.getUniqueId();
            if (!arena.get().claim(playerId)) return ServiceResult.fail("arena-unavailable");
            if (!teleports.teleport(player, arena.get().spawn())) {
                arenas.leave(arena.get().id(), playerId);
                return ServiceResult.fail("world-unavailable");
            }
            applyKit(player, kit.get(), customKits.find(playerId, kit.get().id()).orElse(null));
            session.spectatorTarget(null);
            session.state(FfaState.FFA);
            return ServiceResult.ok("stopped-spectating");
        } finally {
            arenas.release(arena.get(), player.getUniqueId());
        }
    }

    public void recover(Player player) {
        sessions.get(player.getUniqueId()).ifPresent(session -> {
            if (session.kitId() == null || session.arenaId() == null) return;
            kits.find(session.kitId()).ifPresent(kit -> {
                applyKit(player, kit, customKits.find(player.getUniqueId(), kit.id()).orElse(null));
                teleports.normalize(player);
                session.state(FfaState.FFA);
            });
        });
    }

    public void cleanup(Player player) {
        sessions.get(player.getUniqueId()).ifPresent(session -> {
            if (session.arenaId() != null) arenas.leave(session.arenaId(), player.getUniqueId());
        });
        sessions.remove(player.getUniqueId());
    }

    public void targetLeft(java.util.UUID targetId) {
        for (PlayerSession session : sessions.active()) {
            if (!targetId.equals(session.spectatorTarget())) continue;
            org.bukkit.entity.Player spectator = org.bukkit.Bukkit.getPlayer(session.playerId());
            if (spectator != null) stopSpectating(spectator);
        }
    }

    private static void applyKit(Player player, KitDefinition kit, PlayerKitData custom) {
        player.getInventory().clear();
        if (custom == null) {
            player.getInventory().setArmorContents(kit.armor().clone());
            player.getInventory().setItemInOffHand(kit.offhand() == null ? null : kit.offhand().clone());
            kit.inventory().forEach((slot, item) -> player.getInventory().setItem(slot, item.clone()));
        } else {
            player.getInventory().setContents(custom.inventory());
            player.getInventory().setArmorContents(custom.armor());
            player.getInventory().setItemInOffHand(custom.offhand());
        }
        player.setGameMode(kit.gameMode());
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
    }

    private static void restore(Player player, PlayerSnapshot snapshot) {
        player.getInventory().setContents(snapshot.inventory());
        player.getInventory().setArmorContents(snapshot.armor());
        player.getInventory().setItemInOffHand(snapshot.offhand());
        player.setGameMode(snapshot.gameMode());
        player.setLevel(snapshot.level());
        player.setExp(snapshot.experience());
        player.setFoodLevel(snapshot.foodLevel());
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        snapshot.effects().forEach(effect -> player.addPotionEffect(effect));
        player.setHealth(Math.min(player.getMaxHealth(), snapshot.health()));
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying() && snapshot.allowFlight());
        player.setGliding(snapshot.gliding());
        player.setSwimming(snapshot.swimming());
        player.setSprinting(snapshot.sprinting());
        player.setSneaking(snapshot.sneaking());
        player.getInventory().setHeldItemSlot(snapshot.heldItemSlot());
        player.setVelocity(snapshot.velocity() == null ? new Vector() : snapshot.velocity().clone());
    }
}
