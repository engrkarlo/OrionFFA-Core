package com.karlo.orionffa;

import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.arena.ArenaResetService;
import com.karlo.orionffa.combat.CombatManager;
import com.karlo.orionffa.command.OrionFFACommand;
import com.karlo.orionffa.config.ConfigManager;
import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.gui.GuiManager;
import com.karlo.orionffa.kit.KitManager;
import com.karlo.orionffa.kit.KitPersistenceManager;
import com.karlo.orionffa.storage.MySqlStorageProvider;
import com.karlo.orionffa.listener.GameplayListener;
import com.karlo.orionffa.listener.GuiProtectionListener;
import com.karlo.orionffa.listener.PartyChatListener;
import com.karlo.orionffa.storage.StorageMigrationService;
import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.party.PartyManager;
import com.karlo.orionffa.party.PartyMatchService;
import com.karlo.orionffa.player.PlayerSessionManager;
import com.karlo.orionffa.player.TeleportService;
import com.karlo.orionffa.recovery.RespawnRecoveryService;
import com.karlo.orionffa.statistics.StatisticsManager;
import com.karlo.orionffa.storage.StorageProvider;
import com.karlo.orionffa.storage.YamlStorageProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OrionFFAPlugin extends JavaPlugin {
    private final List<BukkitTask> tasks = new ArrayList<>();
    private StorageProvider storage;
    private StatisticsManager statistics;
    private PlayerSessionManager sessions;
    private FfaService ffa;
    private StorageMigrationService migration;
    private BukkitTask arenaResetTask;

    @Override
    public void onEnable() {
        ConfigManager config = new ConfigManager(this);
        MessageService messages = new MessageService(this);
        KitManager kits = new KitManager(config);
        ArenaManager arenas = new ArenaManager(this, config, kits);
        sessions = new PlayerSessionManager();
        TeleportService teleports = new TeleportService();
        storage = createStorage(config);
        migration = new StorageMigrationService(this, kits, storage);
        KitPersistenceManager customKits = new KitPersistenceManager(this, storage);
        ArenaResetService arenaReset = new ArenaResetService(this, arenas, createSchematicService());
        ffa = new FfaService(config, sessions, kits, arenas, teleports, customKits, arenaReset);
        statistics = new StatisticsManager(this, storage);
        CombatManager combat = new CombatManager(sessions, config.runtime().killerCredit());
        PartyManager parties = new PartyManager(config.runtime().partyMaxSize(), config.runtime().partyInviteDuration());
        PartyMatchService matches = new PartyMatchService(config, parties, arenas, kits, sessions, teleports, ffa, arenaReset);
        RespawnRecoveryService recovery = new RespawnRecoveryService(this, sessions, arenas, ffa);
        GuiManager guis = new GuiManager(this, config, messages, ffa, kits, sessions, parties, statistics);
        OrionFFACommand root = new OrionFFACommand(this, config, messages, ffa, guis, kits, arenas, parties, matches, sessions, statistics, combat, arenaReset, migration, storage);
        PluginCommand command = Objects.requireNonNull(getCommand("orionffa"), "orionffa command missing from plugin.yml");
        command.setExecutor(root);
        command.setTabCompleter(root);

        Bukkit.getPluginManager().registerEvents(new GameplayListener(sessions, ffa, combat, statistics, recovery, parties, matches, customKits, kits), this);
        Bukkit.getPluginManager().registerEvents(new GuiProtectionListener(guis), this);
        Bukkit.getPluginManager().registerEvents(new PartyChatListener(this, parties), this);
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, combat::cleanup, 20L, 20L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, statistics::flush, 6_000L, 6_000L));
        rescheduleArenaResets(config, arenas, arenaReset);
        registerPlaceholderApi(statistics);
        reportIntegrations();
        getLogger().info("OrionFFA-Core enabled with " + kits.available().size() + " kits and " + arenas.names().size() + " arenas.");
    }

    @Override
    public void onDisable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (arenaResetTask != null) { arenaResetTask.cancel(); arenaResetTask = null; }
        if (ffa != null) Bukkit.getOnlinePlayers().forEach(ffa::leave);
        if (statistics != null) statistics.flush();
        if (storage != null) storage.close();
        if (sessions != null) sessions.clear();
    }

    private StorageProvider createStorage(ConfigManager config) {
        if ("mysql".equalsIgnoreCase(config.file().getString("storage.type", "yaml"))) {
            try {
                getLogger().info("Initializing MySQL storage...");
                return new MySqlStorageProvider(this);
            } catch (RuntimeException exception) {
                getLogger().warning("MySQL unavailable; falling back to YAML: " + exception.getMessage());
            }
        }
        return new YamlStorageProvider(this);
    }

    public void rescheduleArenaResets(ConfigManager config, ArenaManager arenas, ArenaResetService reset) {
        if (arenaResetTask != null) { arenaResetTask.cancel(); arenaResetTask = null; }
        if (!config.file().getBoolean("arena-reset.enabled", true) || !config.file().getBoolean("arena-reset.schedule.enabled", false)) return;
        long ticks=Math.max(30L, config.file().getLong("arena-reset.schedule.interval-seconds",300L))*20L;
        arenaResetTask=Bukkit.getScheduler().runTaskTimer(this,()->arenas.names().forEach(id->arenas.get(id).ifPresent(a->{if(a.occupants()==0&&!reset.isResetting(id))reset.reset(id);})),ticks,ticks);
    }

    private com.karlo.orionffa.arena.SchematicService createSchematicService() {
        if (!getServer().getPluginManager().isPluginEnabled("WorldEdit") && !getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) return null;
        try {
            Class<?> type=Class.forName("com.karlo.orionffa.arena.WorldEditSchematicService");
            return (com.karlo.orionffa.arena.SchematicService)type.getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("WorldEdit/FAWE detected but the schematic adapter could not be initialized: "+e.getMessage()); return null;
        }
    }

    private void registerPlaceholderApi(StatisticsManager statistics) {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            Class<?> type=Class.forName("com.karlo.orionffa.integration.PlaceholderApiHook");
            Object hook=type.getConstructor(JavaPlugin.class, StatisticsManager.class).newInstance(this,statistics);
            type.getMethod("register").invoke(hook);
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("PlaceholderAPI detected but the integration could not be initialized: "+e.getMessage());
        }
    }

    private void reportIntegrations() {
        for (String name : List.of("PlaceholderAPI", "WorldEdit", "FastAsyncWorldEdit", "Multiverse-Inventories", "PlayerKits")) {
            getLogger().info(name + ": " + (Bukkit.getPluginManager().isPluginEnabled(name) ? "detected" : "not installed"));
        }
    }
}
