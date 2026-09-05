package com.karlo.orionffa.command;

import com.karlo.orionffa.arena.Arena;
import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.arena.ArenaResetService;
import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.ffa.ServiceResult;
import com.karlo.orionffa.gui.GuiManager;
import com.karlo.orionffa.kit.KitManager;
import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.party.PartyManager;
import com.karlo.orionffa.party.PartyResult;
import com.karlo.orionffa.party.PartyMatchService;
import com.karlo.orionffa.player.FfaState;
import com.karlo.orionffa.player.PlayerSessionManager;
import com.karlo.orionffa.statistics.StatisticsManager;
import com.karlo.orionffa.storage.StorageMigrationService;
import com.karlo.orionffa.storage.StorageProvider;
import org.bukkit.plugin.java.JavaPlugin;
import com.karlo.orionffa.combat.CombatManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class OrionFFACommand implements CommandExecutor, TabCompleter {
    private final ConfigManager config;
    private final MessageService messages;
    private final FfaService ffa;
    private final GuiManager guis;
    private final KitManager kits;
    private final ArenaManager arenas;
    private final PartyManager parties;
    private final PartyMatchService matches;
    private final PlayerSessionManager sessions;
    private final StatisticsManager statistics;
    private final CombatManager combat;
    private final ArenaResetService arenaReset;
    private final StorageMigrationService migration;
    private final StorageProvider storage;
    private final JavaPlugin plugin;

    public OrionFFACommand(JavaPlugin plugin, ConfigManager config, MessageService messages, FfaService ffa, GuiManager guis, KitManager kits,
                           ArenaManager arenas, PartyManager parties, PartyMatchService matches, PlayerSessionManager sessions,
                           StatisticsManager statistics, CombatManager combat, ArenaResetService arenaReset, StorageMigrationService migration, StorageProvider storage) {
        this.plugin=plugin;
        this.config = config;
        this.messages = messages;
        this.ffa = ffa;
        this.guis = guis;
        this.kits = kits;
        this.arenas = arenas;
        this.parties = parties;
        this.matches = matches;
        this.sessions = sessions;
        this.statistics = statistics;
        this.combat = combat;
        this.arenaReset = arenaReset;
        this.migration=migration; this.storage=storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) guis.openMain(player);
            else help(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "help" -> { help(sender); yield true; }
            case "menu" -> menu(sender);
            case "join" -> join(sender, args);
            case "editkit" -> editKit(sender, args);
            case "kit" -> kit(sender, args);
            case "back" -> back(sender);
            case "spectate" -> spectate(sender, args);
            case "setlobby" -> setLocation(sender, true);
            case "seteditkit" -> setLocation(sender, false);
            case "reload" -> reload(sender);
            case "status", "debug" -> status(sender);
            case "arena" -> arena(sender, args);
            case "party" -> party(sender, args);
            case "force" -> force(sender, args);
            case "storage" -> storage(sender, args);
            default -> { messages.send(sender, "unknown-command"); yield true; }
        };
    }

    private boolean menu(CommandSender sender) {
        Player player = player(sender);
        if (player != null) guis.openMain(player);
        return true;
    }

    private boolean join(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) return true;
        if (!use(sender)) return true;
        if (args.length < 2) return usage(sender, "/orionffa join <kit>");
        respond(player, ffa.joinKit(player, args[1]));
        return true;
    }

    private boolean editKit(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !use(sender)) return true;
        if (args.length < 2) return usage(sender, "/orionffa editkit <kit>");
        respond(player, ffa.editKit(player, args[1]));
        return true;
    }

    private boolean kit(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !use(sender)) return true;
        if (args.length < 2) return usage(sender, "/orionffa kit <save|leave> [kit]");
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "save" -> {
                String kit = args.length >= 3 ? args[2] : sessions.get(player.getUniqueId()).map(s -> s.kitId()).orElse(null);
                respond(player, ffa.saveEditedKit(player, kit));
                yield true;
            }
            case "leave" -> { respond(player, ffa.leave(player)); yield true; }
            default -> usage(sender, "/orionffa kit <save|leave> [kit]");
        };
    }

    private boolean back(CommandSender sender) {
        Player player = player(sender);
        if (player != null && use(sender)) respond(player, ffa.leave(player));
        return true;
    }

    private boolean spectate(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !use(sender)) return true;
        if (args.length < 2) return usage(sender, "/orionffa spectate <player>");
        Player target = Bukkit.getPlayerExact(args[1]);
        respond(player, target == null ? ServiceResult.fail("spectate-unavailable") : ffa.startSpectating(player, target));
        return true;
    }

    private boolean setLocation(CommandSender sender, boolean lobby) {
        Player player = player(sender);
        if (player == null || !admin(sender)) return true;
        if (lobby) {
            config.setLobby(player.getLocation());
            messages.send(player, "lobby-set");
        } else {
            config.setEditKit(player.getLocation());
            messages.send(player, "edit-kit-set");
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!admin(sender)) return true;
        config.reload();
        messages.reload();
        kits.reload();
        arenas.reload();
        if (plugin instanceof com.karlo.orionffa.OrionFFAPlugin p) p.rescheduleArenaResets(config, arenas, arenaReset);
        if (guis.reload()) messages.send(sender, "config-reloaded");
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!admin(sender)) return true;
        sender.sendMessage(messages.component("<gold>OrionFFA Diagnostics</gold>"));
        sender.sendMessage(messages.component("<gray>Active FFA sessions: <white>" + sessions.active().size()));
        sender.sendMessage(messages.component("<gray>Combat sessions: <white>" + combat.activeCount()));
        sender.sendMessage(messages.component("<gray>Active parties: <white>" + parties.activeCount()));
        sender.sendMessage(messages.component("<gray>Pending storage writes: <white>" + statistics.pendingWrites()));
        return true;
    }

    private boolean force(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 3) return usage(sender, "/orionffa force <player> <kit>");
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) return error(sender, "That player is not online.");
        respond(target, ffa.joinKit(target, args[2]));
        return true;
    }

    private boolean arena(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 2) return usage(sender, "/orionffa arena <save|list|bind|delete|reset>");
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                sender.sendMessage(messages.component("<gold>Arenas:</gold> <white>" + String.join(", ", arenas.names())));
                yield true;
            }
            case "save" -> {
                Player player = player(sender);
                if (player == null) yield true;
                if (args.length < 3) yield usage(sender, "/orionffa arena save <name>");
                if (arenas.save(args[2], player.getLocation())) messages.send(sender, "arena-saved", Map.of("arena", args[2]));
                else error(sender, "Arena names use 1-32 letters, numbers, underscores, or hyphens.");
                yield true;
            }
            case "bind" -> {
                if (args.length < 4) yield usage(sender, "/orionffa arena bind <arena> <kit>");
                if (arenas.bind(args[2], args[3])) messages.send(sender, "arena-bound", Map.of("arena", args[2], "kit", args[3]));
                else error(sender, "The arena or enabled kit does not exist.");
                yield true;
            }
            case "delete" -> {
                if (args.length < 3) yield usage(sender, "/orionffa arena delete <arena>");
                if (arenas.delete(args[2])) messages.send(sender, "arena-deleted", Map.of("arena", args[2]));
                else error(sender, "That arena does not exist.");
                yield true;
            }
            case "reset" -> {
                if (args.length < 3) yield usage(sender, "/orionffa arena reset <arena>");
                if (arenas.get(args[2]).isEmpty()) error(sender, "That arena does not exist.");
                else arenaReset.reset(args[2]).thenAccept(result -> messages.send(sender, result.messageKey()));
                yield true;
            }
            default -> usage(sender, "/orionffa arena <save|list|bind|delete|reset>");
        };
    }

    private boolean party(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !use(sender)) return true;
        if (args.length < 2) return usage(sender, "/orionffa party <create|invite|join|kick|promote|leave|disband|chat|fight|split>");
        PartyResult result;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> { result = parties.create(player.getUniqueId()); return partyResult(player, result, "party-created"); }
            case "invite" -> {
                if (args.length < 3) return usage(player, "/orionffa party invite <player>");
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) return error(player, "That player is not online.");
                result = parties.invite(player.getUniqueId(), target.getUniqueId());
                if (result.success()) {
                    messages.send(player, "party-invite", Map.of("player", target.getName()));
                    messages.send(target, "party-invited", Map.of("player", player.getName()));
                    return true;
                }
                return partyResult(player, result, "");
            }
            case "join" -> {
                if (args.length < 3) return usage(player, "/orionffa party join <leader>");
                Player leader = Bukkit.getPlayerExact(args[2]);
                return partyResult(player, leader == null ? PartyResult.fail("That party leader is not online.") : parties.join(player.getUniqueId(), leader.getUniqueId()), "party-joined");
            }
            case "kick" -> {
                if (args.length < 3) return usage(player, "/orionffa party kick <player>");
                Player target = Bukkit.getPlayerExact(args[2]);
                return partyResult(player, target == null ? PartyResult.fail("That player is not online.") : parties.kick(player.getUniqueId(), target.getUniqueId()), "");
            }
            case "promote" -> {
                if (args.length < 3) return usage(player, "/orionffa party promote <player>");
                Player target = Bukkit.getPlayerExact(args[2]);
                return partyResult(player, target == null ? PartyResult.fail("That player is not online.") : parties.promote(player.getUniqueId(), target.getUniqueId()), "");
            }
            case "leave" -> { return partyResult(player, parties.leave(player.getUniqueId()), "party-left"); }
            case "disband" -> { return partyResult(player, parties.disband(player.getUniqueId()), "party-disbanded"); }
            case "chat" -> {
                if (parties.find(player.getUniqueId()).isEmpty()) return partyResult(player, PartyResult.fail("You are not in a party."), "");
                boolean enabled = parties.toggleChatState(player.getUniqueId());
                player.sendMessage(messages.component(enabled ? "<green>Party chat enabled." : "<yellow>Party chat disabled."));
                return true;
            }
            case "fight" -> {
                String kit = args.length >= 3 ? args[2] : null;
                PartyResult r = matches.start(player, false, kit);
                if (r.success()) player.sendMessage(messages.component("<green>Party fight started.</green>"));
                else player.sendMessage(messages.component("<red>" + r.reason() + "</red>"));
                return true;
            }
            case "split" -> {
                String kit = args.length >= 3 ? args[2] : null;
                PartyResult r = matches.start(player, true, kit);
                if (r.success()) player.sendMessage(messages.component("<green>Party split match started.</green>"));
                else player.sendMessage(messages.component("<red>" + r.reason() + "</red>"));
                return true;
            }
            default -> { return usage(player, "/orionffa party <create|invite|join|kick|promote|leave|disband|chat|fight|split>"); }
        }
    }

    private boolean storage(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 2 || "status".equalsIgnoreCase(args[1])) {
            sender.sendMessage(messages.component("<gray>Storage: <white>" + storage.type().toUpperCase(Locale.ROOT) + " <gray>| Pending writes: <white>" + statistics.pendingWrites()));
        } else {
            migration.migrateYamlToTarget().thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.component(result.success() ? "<green>Storage migration completed for " + result.players() + " players.</green>" : "<red>Storage migration failed: " + result.message() + "</red>"))));
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(messages.component("<gold>OrionFFA</gold> <gray>— /orionffa <subcommand>"));
        if (use(sender)) sender.sendMessage(messages.component("<yellow>join <kit>, editkit <kit>, kit save|leave, menu, back, spectate <player>, party"));
        if (admin(sender)) sender.sendMessage(messages.component("<yellow>force, setlobby, seteditkit, reload, status, arena, storage"));
    }

    private boolean partyResult(Player player, PartyResult result, String successKey) {
        if (result.success()) {
            messages.send(player, successKey);
        } else {
            messages.send(player, "party-error", Map.of("reason", result.reason()));
        }
        return true;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        messages.send(sender, "player-only");
        return null;
    }

    private boolean use(CommandSender sender) {
        if (sender.hasPermission("orionffa.use") || sender.hasPermission("orionffa.admin")) return true;
        messages.send(sender, "no-permission");
        return false;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("orionffa.admin")) return true;
        messages.send(sender, "no-permission");
        return false;
    }

    private boolean usage(CommandSender sender, String syntax) {
        messages.send(sender, "usage", Map.of("usage", syntax));
        return true;
    }

    private boolean error(CommandSender sender, String reason) {
        messages.send(sender, "party-error", Map.of("reason", reason));
        return true;
    }

    private void respond(Player player, ServiceResult result) {
        messages.send(player, result.messageKey(), result.placeholders());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(topLevel(sender), args[0]);
        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) return switch (root) {
            case "join" -> filter(kits.names(), args[1]);
            case "spectate" -> filter(eligiblePlayers(sender), args[1]);
            case "force" -> admin(sender) ? filter(eligiblePlayers(sender), args[1]) : List.of();
            case "editkit" -> filter(kits.names(), args[1]);
            case "kit" -> use(sender) ? filter(List.of("save", "leave"), args[1]) : List.of();
            case "arena" -> admin(sender) ? filter(List.of("save", "list", "bind", "delete", "reset"), args[1]) : List.of();
            case "party" -> use(sender) ? filter(partyActions(sender), args[1]) : List.of();
            case "storage" -> admin(sender) ? filter(List.of("status", "migrate", "stats"), args[1]) : List.of();
            case "help" -> filter(topLevel(sender), args[1]);
            default -> List.of();
        };
        if (args.length == 3 && root.equals("force")) return admin(sender) ? filter(kits.names(), args[2]) : List.of();
        if (args.length == 3 && root.equals("editkit")) return List.of();
        if (args.length == 3 && root.equals("kit") && args[1].equalsIgnoreCase("save")) return filter(kits.names(), args[2]);
        if (args.length == 3) return switch (root + " " + args[1].toLowerCase(Locale.ROOT)) {
            case "arena bind" -> admin(sender) ? filter(arenas.names(), args[2]) : List.of();
            case "arena delete", "arena reset" -> admin(sender) ? filter(arenas.names(), args[2]) : List.of();
            case "party invite", "party kick", "party promote", "party join" -> filter(eligiblePlayers(sender), args[2]);
            default -> List.of();
        };
        if (args.length == 4 && root.equals("arena") && args[1].equalsIgnoreCase("bind")) return admin(sender) ? filter(kits.names(), args[3]) : List.of();
        return List.of();
    }

    private List<String> topLevel(CommandSender sender) {
        List<String> commands = new ArrayList<>(List.of("help"));
        if (use(sender)) commands.addAll(List.of("menu", "join", "editkit", "kit", "back", "spectate", "party"));
        if (admin(sender)) commands.addAll(List.of("force", "setlobby", "seteditkit", "reload", "status", "debug", "arena", "storage"));
        return commands;
    }

    private List<String> partyActions(CommandSender sender) {
        if (!(sender instanceof Player player)) return List.of();
        return parties.find(player.getUniqueId()).isPresent()
                ? List.of("invite", "kick", "promote", "leave", "disband", "chat", "fight", "split")
                : List.of("create", "join");
    }

    private List<String> eligiblePlayers(CommandSender sender) {
        return Bukkit.getOnlinePlayers().stream().filter(player -> !player.equals(sender)).map(Player::getName).toList();
    }

    private static List<String> filter(Collection<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted(Comparator.naturalOrder()).toList();
    }
}
