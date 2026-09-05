package com.karlo.orionffa.party;

import com.karlo.orionffa.arena.Arena;
import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.arena.ArenaResetService;
import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.kit.KitDefinition;
import com.karlo.orionffa.kit.KitManager;
import com.karlo.orionffa.player.FfaState;
import com.karlo.orionffa.player.PlayerSessionManager;
import com.karlo.orionffa.player.TeleportService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the complete lifecycle of party-only matches. Bukkit mutations stay on the server thread. */
public final class PartyMatchService {
    private final ConfigManager config;
    private final PartyManager parties;
    private final ArenaManager arenas;
    private final KitManager kits;
    private final PlayerSessionManager sessions;
    private final TeleportService teleports;
    private final FfaService ffa;
    private final ArenaResetService resets;
    private final Map<UUID, PartyMatch> matches = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byPlayer = new ConcurrentHashMap<>();

    public PartyMatchService(ConfigManager config, PartyManager parties, ArenaManager arenas, KitManager kits,
                             PlayerSessionManager sessions, TeleportService teleports, FfaService ffa,
                             ArenaResetService resets) {
        this.config = config;
        this.parties = parties;
        this.arenas = arenas;
        this.kits = kits;
        this.sessions = sessions;
        this.teleports = teleports;
        this.ffa = ffa;
        this.resets = resets;
    }

    public Optional<PartyMatch> find(UUID playerId) {
        UUID matchId = byPlayer.get(playerId);
        return matchId == null ? Optional.empty() : Optional.ofNullable(matches.get(matchId));
    }

    public PartyResult start(Player leader, boolean split, String kitId) {
        if (!config.file().getBoolean("split.enabled", true)) return PartyResult.fail("Party matches are disabled.");
        Party party = parties.find(leader.getUniqueId()).orElse(null);
        if (party == null) return PartyResult.fail("Create a party first.");
        if (!party.leader().equals(leader.getUniqueId())) return PartyResult.fail("Only the party leader can start a match.");
        if (party.activeMatch() != null) return PartyResult.fail("Your party already has an active match.");
        int minimum = Math.max(2, config.file().getInt("split.minimum-size", 2));
        if (party.members().size() < minimum) return PartyResult.fail("Your party needs at least " + minimum + " players.");

        KitDefinition kit = kitId == null || kitId.isBlank()
                ? kits.available().stream().findFirst().orElse(null)
                : kits.find(kitId).orElse(null);
        if (kit == null) return PartyResult.fail("That kit is unavailable.");

        List<UUID> members = new ArrayList<>(party.members());
        for (UUID id : members) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player == null) return PartyResult.fail("All party members must be online.");
            if (sessions.get(id).map(s -> s.state() != FfaState.OUTSIDE).orElse(false)) return PartyResult.fail("All party members must be outside FFA.");
        }

        Arena arena = arenas.availableFor(kit.id()).stream()
                .filter(a -> a.capacity() >= members.size())
                .filter(a -> !resets.isResetting(a.id()))
                .filter(a -> !split || (a.splitSpawnA() != null && a.splitSpawnB() != null))
                .findFirst().orElse(null);
        if (arena == null) return PartyResult.fail(split ? "No arena with two configured split spawns is available." : "No match arena is available.");

        Set<UUID> participants = new HashSet<>(members);
        Map<String, Set<UUID>> teams = buildTeams(members, split);
        PartyMatch match = new PartyMatch(party.id(), arena.id(), teams, participants);
        UUID matchId = match.id();
        // Reserve every member before changing any player state.
        List<UUID> claimed = new ArrayList<>();
        for (UUID id : members) {
            if (!arena.reserve(id, java.time.Instant.now().plusSeconds(15).plusSeconds(members.size()))) {
                claimed.forEach(arena::release);
                return PartyResult.fail("The selected match arena became unavailable.");
            }
            if (!arena.claim(id)) {
                claimed.forEach(arena::leave);
                arena.release(id);
                return PartyResult.fail("The selected match arena became unavailable.");
            }
            claimed.add(id);
        }

        party.activeMatch(matchId);
        matches.put(matchId, match);
        for (UUID id : members) byPlayer.put(id, matchId);
        match.state(PartyMatchState.BUILDING);
        try {
            for (UUID id : members) {
                Player player = org.bukkit.Bukkit.getPlayer(id);
                if (player == null) throw new IllegalStateException("Party member disconnected during match setup");
                var session = sessions.enter(player);
                session.state(FfaState.PARTY_MATCH);
                session.kitId(kit.id());
                session.arenaId(arena.id());
                teleports.prepare(player);
                var team = match.teamOf(id);
                var spawn = split ? ("team-a".equals(team) ? arena.splitSpawnA() : arena.splitSpawnB()) : arena.spawn();
                if (!teleports.teleport(player, spawn)) throw new IllegalStateException("Could not teleport a match participant");
                applyKit(player, kit);
            }
            match.state(PartyMatchState.ACTIVE);
            return PartyResult.ok();
        } catch (RuntimeException failure) {
            finish(matchId);
            return PartyResult.fail("The match could not be started; all players were restored.");
        }
    }

    public boolean shouldCancelDamage(Player attacker, Player victim) {
        PartyMatch a = find(attacker.getUniqueId()).orElse(null);
        PartyMatch v = find(victim.getUniqueId()).orElse(null);
        if (a == null || a != v || a.state() != PartyMatchState.ACTIVE) return false;
        return a.friendly(attacker.getUniqueId(), victim.getUniqueId());
    }

    public boolean handleDeath(Player victim) {
        PartyMatch match = find(victim.getUniqueId()).orElse(null);
        if (match == null || match.state() != PartyMatchState.ACTIVE) return false;
        List<UUID> alive = match.participants().stream().filter(id -> {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            return p != null && !p.isDead() && !id.equals(victim.getUniqueId());
        }).toList();
        Set<String> aliveTeams = new HashSet<>();
        for (UUID id : alive) aliveTeams.add(match.teamOf(id));
        if (aliveTeams.size() <= 1) org.bukkit.Bukkit.getScheduler().runTask(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () -> finish(match.id()));
        return true;
    }

    public boolean recover(Player player) {
        PartyMatch match = find(player.getUniqueId()).orElse(null);
        if (match == null || match.state() != PartyMatchState.ACTIVE) return false;
        Arena arena = arenas.get(match.arenaId()).orElse(null);
        KitDefinition kit = kits.find(sessions.get(player.getUniqueId()).map(s -> s.kitId()).orElse("")).orElse(null);
        if (arena == null || kit == null) return false;
        String team = match.teamOf(player.getUniqueId());
        var spawn = "team-a".equals(team) ? arena.splitSpawnA() : "team-b".equals(team) ? arena.splitSpawnB() : arena.spawn();
        if (!teleports.teleport(player, spawn)) return false;
        applyKit(player, kit);
        sessions.get(player.getUniqueId()).ifPresent(s -> s.state(FfaState.PARTY_MATCH));
        return true;
    }

    public void finish(UUID matchId) {
        PartyMatch match = matches.get(matchId);
        if (match == null || match.state() == PartyMatchState.CLEANUP) return;
        match.state(PartyMatchState.FINISHED);
        match.state(PartyMatchState.CLEANUP);
        for (UUID id : match.participants()) {
            byPlayer.remove(id, matchId);
            Player player = org.bukkit.Bukkit.getPlayer(id);
            if (player != null) ffa.leave(player);
            arenas.leave(match.arenaId(), id);
        }
        parties.clearActiveMatch(match.partyId(), matchId);
        matches.remove(matchId);
    }

    public void disconnect(UUID playerId) {
        find(playerId).ifPresent(match -> finish(match.id()));
    }

    private static Map<String, Set<UUID>> buildTeams(List<UUID> members, boolean split) {
        Map<String, Set<UUID>> teams = new HashMap<>();
        if (!split) {
            for (int i = 0; i < members.size(); i++) teams.put("player-" + i, Set.of(members.get(i)));
            return teams;
        }
        Set<UUID> a = new HashSet<>(), b = new HashSet<>();
        List<UUID> shuffled = new ArrayList<>(members);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) (i % 2 == 0 ? a : b).add(shuffled.get(i));
        teams.put("team-a", Set.copyOf(a));
        teams.put("team-b", Set.copyOf(b));
        return Map.copyOf(teams);
    }

    private static void applyKit(Player player, KitDefinition kit) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(kit.armor().clone());
        player.getInventory().setItemInOffHand(kit.offhand() == null ? null : kit.offhand().clone());
        kit.inventory().forEach((slot, item) -> player.getInventory().setItem(slot, item.clone()));
        player.setGameMode(kit.gameMode());
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0);
    }
}
