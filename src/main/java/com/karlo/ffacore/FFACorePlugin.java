package com.karlo.ffacore;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FFACorePlugin extends JavaPlugin implements Listener {
    private final Map<UUID,String> kit = new ConcurrentHashMap<>();
    private final Map<UUID,String> editing = new ConcurrentHashMap<>();
    private final Map<UUID,Long> combat = new ConcurrentHashMap<>();
    private final Map<UUID,UUID> lastAttacker = new ConcurrentHashMap<>();
    private final Map<UUID,Long> lastHit = new ConcurrentHashMap<>();
    private final Map<UUID,Long> crystalPlace = new ConcurrentHashMap<>();
    private final Map<UUID,InventorySnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID,Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID,UUID> partyMembership = new ConcurrentHashMap<>();
    private final Map<UUID,Boolean> partyChat = new ConcurrentHashMap<>();
    private final Map<String,SplitMatch> splits = new ConcurrentHashMap<>();
    private final Map<UUID,String> playerSplit = new ConcurrentHashMap<>();
    private final Set<UUID> ffaPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRecovery = ConcurrentHashMap.newKeySet();
    private final Map<UUID,String> gui = new ConcurrentHashMap<>();
    private final Map<UUID,Location> respawnLocations = new ConcurrentHashMap<>();
    private final Map<String,Long> splitArenaLocks = new ConcurrentHashMap<>();
    private ExecutorService resetExecutor;
    private BukkitTask tickTask;
    private BukkitTask clearTask;
    private BukkitTask safezoneTask;
    private BukkitTask arenaResetTask;
    private Path customKitsFile;
    private YamlConfiguration customKits;

    // Optional MySQL storage. Bukkit work remains on the main thread; database I/O is async.
    private ExecutorService storageExecutor;
    private volatile boolean mysqlReady = false;
    private final Set<UUID> mysqlLoadedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> mysqlLoadingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, CustomKitData> customKitCache = new ConcurrentHashMap<>();
    private final Map<String, StatsData> statsCache = new ConcurrentHashMap<>();
    private final Set<String> statsLoading = ConcurrentHashMap.newKeySet();
    private PlaceholderExpansion placeholderExpansion;

    private NamespacedKey selectorKey;
    private NamespacedKey guiKey;
    private NamespacedKey kitKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        customKitsFile = new File(getDataFolder(), "custom-kits.yml").toPath();
        loadCustomKits();
        selectorKey = new NamespacedKey(this, "selector");
        guiKey = new NamespacedKey(this, "gui");
        kitKey = new NamespacedKey(this, "kit");
        storageExecutor = Executors.newFixedThreadPool(Math.max(1, getConfig().getInt("storage.mysql.worker-threads", 2)), r -> { Thread t=new Thread(r,"OrionFFACore-Storage"); t.setDaemon(true); return t; });
        initStorageAsync();
        initStatsTable();
        resetExecutor = Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"OrionFFACore-FAWE"); t.setDaemon(true); return t; });
        getServer().getPluginManager().registerEvents(this, this);
        registerPostRespawn();
        registerPlaceholderAPI();
        registerCommands();
        startTasks();
        enforceImmediateRespawn();
        startArenaResetScheduler();
        getLogger().info("OrionFFACore 2.3.0 enabled for world: " + ffaWorldName());
        getLogger().info("Native respawn/pose recovery: " + getConfig().getBoolean("recovery.enabled", true));
        getLogger().info("FAWE arena reset integration: " + getConfig().getBoolean("arena-reset.enabled", true) + " | schedule=" + getConfig().getBoolean("arena-reset.schedule.enabled", true) + " | interval=" + getConfig().getLong("arena-reset.schedule.interval-seconds", 300) + "s");
        if (getServer().getPluginManager().getPlugin("Multiverse-Inventories") != null) getLogger().info("Multiverse-Inventories detected; OrionFFACore will not take ownership of cross-world inventory switching.");
        if (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null && getConfig().getBoolean("arena-reset.enabled", true)) getLogger().warning("FastAsyncWorldEdit not detected. Arena reset commands will report unavailable until FAWE is installed.");
    }

    @Override public void onDisable() {
        if (tickTask != null) tickTask.cancel(); if (clearTask != null) clearTask.cancel(); if (safezoneTask != null) safezoneTask.cancel(); if (arenaResetTask != null) arenaResetTask.cancel();
        if (resetExecutor != null) resetExecutor.shutdownNow();
        if (storageExecutor != null) storageExecutor.shutdownNow();
        saveCustomKits();
        saveStatsYaml();
    }

    private void enforceImmediateRespawn(){
        if(!getConfig().getBoolean("server.require-immediate-respawn",true))return;
        World w=Bukkit.getWorld(ffaWorldName());
        if(w!=null){try{w.setGameRuleValue("doImmediateRespawn","true");getLogger().info("Enabled doImmediateRespawn for FFA world.");}catch(Throwable t){getLogger().warning("Could not set doImmediateRespawn: "+t.getMessage());}}
    }

    private void registerCommands() {
        CommandExecutor exec = (sender, command, label, args) -> handleCommand(sender, command.getName().toLowerCase(Locale.ROOT), args);
        for (String c : List.of("ffa","party","kit","setsplitspawn","ffasafezone")) if (getCommand(c)!=null) getCommand(c).setExecutor(exec);
    }

    private boolean handleCommand(CommandSender sender, String cmd, String[] a) {
        if (!(sender instanceof Player p)) { sender.sendMessage(message("&cPlayer-only command.")); return true; }
        if (cmd.equals("ffa")) { if(!p.hasPermission(getConfig().getString("server.player-permission","ffa.use")) && !p.hasPermission("orionffa.use") && !p.hasPermission("ffa.use")){p.sendMessage(msg("no-permission"));return true;} return ffaCommand(p,a); }
        if (cmd.equals("party")) return partyCommand(p,a);
        if (cmd.equals("kit")) return kitCommand(p,a);
        if (cmd.equals("setsplitspawn")) return splitSpawnCommand(p,a);
        if (cmd.equals("ffasafezone")) return safezoneCommand(p,a);
        return true;
    }

    private boolean ffaCommand(Player p,String[] a) {
        if (a.length==0) { p.sendMessage(message("&c/ffa join &7| &c/ffa back &7| &c/ffa menu &7| &c/ffa editkit &7| &c/ffa force <player> &7| &c/ffa setlobby &7| &c/ffa seteditkit")); return true; }
        String sub=a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("join")) { joinFFA(p); return true; }
        if (sub.equals("back")) { backFFA(p,false); return true; }
        if (sub.equals("menu")) { if (!requireFFA(p)) return true; openKitMenu(p); return true; }
        if (sub.equals("editkit")) { if (!requireFFA(p)) return true; openEditMenu(p); return true; }
        if (sub.equals("setlobby")) { if (!admin(p)) return true; saveLocation("locations.lobby",p.getLocation()); p.sendMessage(message("&aFFA lobby saved.")); return true; }
        if (sub.equals("seteditkit")) { if (!admin(p)) return true; saveLocation("locations.edit-kit",p.getLocation()); p.sendMessage(message("&aFFA edit room saved.")); return true; }
        if (sub.equals("force")) { if (!admin(p)) return true; if (a.length<2) {p.sendMessage(message("&cUsage: /ffa force <player>"));return true;} Player t=Bukkit.getPlayerExact(a[1]); if(t==null){p.sendMessage(message("&cPlayer is offline."));return true;} backFFA(t,true); p.sendMessage(message("&aForced &e"+t.getName()+" &aout of FFA.")); return true; }
        if (sub.equals("reload")) { if(!admin(p))return true; reloadConfig(); loadCustomKits(); startArenaResetScheduler(); p.sendMessage(message("&aOrionFFACore configuration reloaded.")); return true; }
        if (sub.equals("reset")) { if(!admin(p))return true; if(a.length<2){p.sendMessage(message("&cUsage: /ffa reset <arena-id>"));return true;} resetArenaAsync(a[1]); p.sendMessage(message("&aArena reset queued: &e"+a[1])); return true; }
        if (sub.equals("arena")) { return arenaCommand(p, a); }
        if (sub.equals("storage")) { return storageCommand(p, a); }
        p.sendMessage(message("&cUnknown FFA subcommand.")); return true;
    }

    private void joinFFA(Player p) {
        if(usingMysql())preloadMysqlKits(p);
        if (!inFFAWorld(p)) { p.sendMessage(message("&cYou must be in the FFA world first.")); return; }
        Location lobby=location("locations.lobby"); if(lobby==null){p.sendMessage(msg("no-lobby"));return;}
        if (!ffaPlayers.contains(p.getUniqueId())) {
            if (inventoryMode().equals("native")) snapshots.put(p.getUniqueId(), InventorySnapshot.capture(p));
            ffaPlayers.add(p.getUniqueId());
        }
        clearCombat(p); kit.remove(p.getUniqueId()); editing.remove(p.getUniqueId()); playerSplit.remove(p.getUniqueId());
        clearPlayer(p); teleport(p,lobby); giveSelector(p); p.sendMessage(msg("join"));
    }

    private void backFFA(Player p, boolean forced) {
        if (!ffaPlayers.contains(p.getUniqueId())) { p.sendMessage(msg("must-join")); return; }
        UUID id=p.getUniqueId();
        p.setGameMode(GameMode.SURVIVAL);
        if (playerSplit.containsKey(id)) endSplitFor(id);
        kit.remove(id); editing.remove(id); clearCombat(p); clearPlayer(p); ffaPlayers.remove(id);
        if (inventoryMode().equals("native")) { InventorySnapshot s=snapshots.remove(id); if(s!=null)s.restore(p); }
        else giveSelector(p);
        Location lobby=location("locations.lobby"); if(lobby!=null) teleport(p,lobby);
        if (!forced) p.sendMessage(msg("back"));
    }

    private boolean kitCommand(Player p,String[] a) {
        if(!requireFFA(p)) return true;
        if(a.length==0){p.sendMessage(message("&c/kit save &7or &c/kit leave"));return true;}
        if(a[0].equalsIgnoreCase("save")) { String k=editing.get(p.getUniqueId()); if(k==null){p.sendMessage(message("&cYou are not editing a kit."));return true;} saveCustomKit(p,k); editing.remove(p.getUniqueId()); clearPlayer(p); Location l=location("locations.lobby"); if(l!=null)teleport(p,l); giveSelector(p); p.sendMessage(message("&aSaved your customized &e"+k+" &akit.")); return true; }
        if(a[0].equalsIgnoreCase("leave")) { editing.remove(p.getUniqueId()); clearPlayer(p); Location l=location("locations.lobby"); if(l!=null)teleport(p,l); giveSelector(p); p.sendMessage(message("&eLeft the edit room.")); return true; }
        return true;
    }

    private void togglePartyChat(Player p){
        if(currentParty(p)==null){p.sendMessage(msg("party-no-party"));return;}
        boolean next=!partyChat.getOrDefault(p.getUniqueId(),false);
        partyChat.put(p.getUniqueId(),next);
        p.sendMessage(msg(next?"party-chat-on":"party-chat-off"));
    }

    private boolean partyCommand(Player p,String[] a) {
        if(!requireFFA(p)) return true;
        if(a.length==0 || a[0].equalsIgnoreCase("gui")){openPartyInfo(p);return true;}
        String s=a[0].toLowerCase(Locale.ROOT);
        if(s.equals("invite")){ if(a.length<2){p.sendMessage(message("&cUsage: /party invite <player>"));return true;} Player t=Bukkit.getPlayerExact(a[1]); if(t==null){p.sendMessage(message("&cPlayer is offline."));return true;} if(!ffaPlayers.contains(t.getUniqueId())){p.sendMessage(message("&cThat player must join FFA first."));return true;} Party party=getOrCreateParty(p); if(party.members.size()>=maxParty()){p.sendMessage(message("&cParty is full."));return true;} party.invites.put(t.getUniqueId(),System.currentTimeMillis()); t.sendMessage(message("&eYou were invited to &6"+p.getName()+"&e's party. Type &a/party join "+p.getName())); p.sendMessage(message("&aInvitation sent.")); return true; }
        if(s.equals("join")){ if(a.length<2){p.sendMessage(message("&cUsage: /party join <leader>"));return true;} Player leader=Bukkit.getPlayerExact(a[1]); if(leader==null){p.sendMessage(message("&cLeader is offline."));return true;} Party party=parties.get(leader.getUniqueId()); if(party==null || !party.invites.containsKey(p.getUniqueId())){p.sendMessage(message("&cNo valid invitation."));return true;} if(kit.containsKey(p.getUniqueId())){p.sendMessage(message("&cLeave your kit first."));return true;} party.invites.remove(p.getUniqueId()); party.members.add(p.getUniqueId()); partyMembership.put(p.getUniqueId(),party.leader); updateSelector(p); p.sendMessage(message("&aJoined the party.")); return true; }
        if(s.equals("leave")){leaveParty(p);return true;}
        if(s.equals("disband")){Party party=parties.get(p.getUniqueId()); if(party==null){p.sendMessage(message("&cYou do not own a party."));return true;} disbandParty(party,true); return true;}
        if(s.equals("kick")){if(a.length<2){p.sendMessage(message("&cUsage: /party kick <player>"));return true;} Party party=parties.get(p.getUniqueId()); if(party==null){p.sendMessage(message("&cYou do not own a party."));return true;} Player t=Bukkit.getPlayerExact(a[1]); if(t!=null && party.members.remove(t.getUniqueId())){partyMembership.remove(t.getUniqueId());updateSelector(t);t.sendMessage(message("&cYou were kicked from the party."));} return true;}
        if(s.equals("promote")){if(a.length<2){return true;} Party party=parties.get(p.getUniqueId()); Player t=Bukkit.getPlayerExact(a[1]); if(party==null||t==null||!party.members.contains(t.getUniqueId())){return true;} party.leader=t.getUniqueId(); parties.remove(p.getUniqueId()); parties.put(t.getUniqueId(),party); for(UUID u:party.members)partyMembership.put(u,t.getUniqueId()); p.sendMessage(message("&aPromoted &e"+t.getName()+" &ato leader.")); return true;}
        if(s.equals("chat")){if(!partyMembership.containsKey(p.getUniqueId())){p.sendMessage(message("&cYou are not in a party."));return true;} partyChat.put(p.getUniqueId(),!partyChat.getOrDefault(p.getUniqueId(),false));p.sendMessage(message("&eParty chat: "+(partyChat.get(p.getUniqueId())?"&aON":"&cOFF")));return true;}
        if(s.equals("fights")){openPartyFights(p);return true;}
        if(s.equals("split")){openSplitMenu(p);return true;}
        p.sendMessage(message("&cUnknown party subcommand.")); return true;
    }

    private boolean splitSpawnCommand(Player p,String[] a){if(!admin(p))return true;if(a.length<3){p.sendMessage(message("&cUsage: /setsplitspawn <kit> <A|B> <arena> [max]"));return true;}String k=a[0].toLowerCase();String team=a[1].toUpperCase();int n=Integer.parseInt(a[2]);getConfig().set("split.arenas."+k+"."+n+"."+team, serializeLoc(p.getLocation()));if(a.length>=4)getConfig().set("split.max-arenas."+k,Integer.parseInt(a[3]));saveConfig();p.sendMessage(message("&aSaved split spawn."));return true;}
    private boolean safezoneCommand(Player p,String[] a){if(!admin(p))return true;if(a.length==0){p.sendMessage(message("&c/ffasafezone pos1|pos2|status|remove"));return true;}if(a[0].equalsIgnoreCase("pos1")||a[0].equalsIgnoreCase("pos2")){saveLocation("safezone."+a[0].toLowerCase(),p.getLocation());saveConfig();p.sendMessage(message("&aSaved safezone "+a[0]+"."));}else if(a[0].equalsIgnoreCase("remove")){getConfig().set("safezone.pos1",null);getConfig().set("safezone.pos2",null);saveConfig();p.sendMessage(message("&aSafezone removed."));}else{p.sendMessage(message("&7Pos1: "+getConfig().getString("safezone.pos1")+" | Pos2: "+getConfig().getString("safezone.pos2")));}return true;}

    private boolean storageCommand(Player p,String[] a){
        if(!admin(p))return true;
        if(a.length==0||a[0].equalsIgnoreCase("status")){
            p.sendMessage(message("&6✦ &eStorage &8• &fmode=&e"+storageMode()+" &8• &fmysql=&e"+(mysqlReady?"READY":"OFFLINE")));
            return true;
        }
        if(a[0].equalsIgnoreCase("stats")) {
            if(a.length < 2) { p.sendMessage(message("&cUsage: /ffa stats <player> [world]")); return true; }
            Player target=Bukkit.getPlayerExact(a[1]);
            UUID u=target!=null?target.getUniqueId():null;
            String world=a.length>=3?a[2]:p.getWorld().getName();
            if(u==null){ try{u=UUID.fromString(a[1]);}catch(Exception ignored){} }
            if(u==null){p.sendMessage(message("&cPlayer is not online and the value is not a UUID."));return true;}
            StatsData st=getStats(u,world);
            p.sendMessage(message("&6✦ &eStats &8• &f"+world+" &8• &aK &f"+st.kills+" &8• &cD &f"+st.deaths+" &8• &eStreak &f"+st.streak+" &8• &bKD &f"+formatKd(st.kd())+" &8• &dBest KD &f"+formatKd(st.bestKd)));
            return true;
        }
        if(a[0].equalsIgnoreCase("migrate")){
            if(!storageMode().equals("mysql")){p.sendMessage(message("&cMySQL mode is not enabled in config.yml."));return true;}
            if(!mysqlReady){p.sendMessage(message("&cMySQL is not connected yet."));return true;}
            migrateYamlToMysqlAsync(p);
            return true;
        }
        p.sendMessage(message("&7Usage: /ffa storage status|migrate"));return true;
    }

    private void migrateYamlToMysqlAsync(Player admin){
        Map<String,CustomKitData> copy=new LinkedHashMap<>();
        ConfigurationSection root=customKits.getConfigurationSection("");
        if(root!=null){
            for(String uuidKey:root.getKeys(false)){
                ConfigurationSection user=customKits.getConfigurationSection(uuidKey);
                if(user==null)continue;
                for(String k:user.getKeys(false)){
                    String base=uuidKey+"."+k;
                    if(customKits.contains(base+".inventory")){
                        copy.put(uuidKey+":"+k,new CustomKitData(customKits.getStringList(base+".inventory"),customKits.getStringList(base+".armor"),customKits.getString(base+".offhand","")));
                    }
                }
            }
        }
        storageExecutor.submit(()->{
            int ok=0;
            try(Connection c=mysqlConnection(); PreparedStatement ps=c.prepareStatement("INSERT INTO `"+mysqlTable()+"` (uuid,kit,inventory,armor,offhand) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE inventory=VALUES(inventory), armor=VALUES(armor), offhand=VALUES(offhand)")){
                for(Map.Entry<String,CustomKitData> e:copy.entrySet()){
                    String[] parts=e.getKey().split(":",2);
                    if(parts.length!=2)continue;
                    ps.setString(1,parts[0]);ps.setString(2,parts[1]);ps.setString(3,joinBlob(e.getValue().inventory));ps.setString(4,joinBlob(e.getValue().armor));ps.setString(5,e.getValue().offhand);
                    ps.addBatch();ok++;
                }
                ps.executeBatch();
                int total=ok;
                Bukkit.getScheduler().runTask(this,()->admin.sendMessage(msg("storage-migrate-done").replace("{count}",String.valueOf(total))));
            }catch(Exception e){
                Bukkit.getScheduler().runTask(this,()->admin.sendMessage(message("&cMySQL migration failed: &7"+e.getMessage())));
            }
        });
    }

    private boolean arenaCommand(Player p,String[] a){
        if(!admin(p))return true;
        if(a.length<2){
            p.sendMessage(message("&c/ffa arena save <id> &7- save your WorldEdit/FAWE selection as a schematic"));
            p.sendMessage(message("&c/ffa arena reset <id> &7- queue a reset"));
            p.sendMessage(message("&c/ffa arena list &7- list configured arenas"));
            return true;
        }
        String sub=a[1].toLowerCase(Locale.ROOT);
        if(sub.equals("save")){
            if(a.length<3){p.sendMessage(message("&cUsage: /ffa arena save <id>"));return true;}
            saveArenaFromSelectionAsync(p,a[2]);
            return true;
        }
        if(sub.equals("reset")){
            if(a.length<3){p.sendMessage(message("&cUsage: /ffa arena reset <id>"));return true;}
            resetArenaAsync(a[2]);
            p.sendMessage(message("&aArena reset queued: &e"+a[2]));
            return true;
        }
        if(sub.equals("list")){
            ConfigurationSection sec=getConfig().getConfigurationSection("arena-reset.arenas");
            if(sec==null||sec.getKeys(false).isEmpty()){p.sendMessage(message("&7No arena reset entries configured."));return true;}
            p.sendMessage(message("&6Arena reset entries:"));
            for(String id:sec.getKeys(false)){ConfigurationSection e=sec.getConfigurationSection(id);if(e==null)continue;boolean enabled=e.getBoolean("enabled",true),scheduled=e.getBoolean("scheduled",true);p.sendMessage(message("&e- "+id+" &7enabled="+enabled+" scheduled="+scheduled+" schematic="+e.getString("schematic","missing")));}
            return true;
        }
        p.sendMessage(message("&cUnknown arena subcommand."));
        return true;
    }

    private void openKitMenu(Player p){List<String> names=new ArrayList<>();ConfigurationSection sec=getConfig().getConfigurationSection("kits");if(sec==null)return;Inventory inv=Bukkit.createInventory(null,36,color(getConfig().getString("gui.kit-title","&8FFA Kits")));int[] slots={10,11,12,13,14,15,16,20,21,22,23,24};int i=0;for(String k:sec.getKeys(false)){if(i>=slots.length)break;ItemStack item=icon(k);inv.setItem(slots[i++],item);names.add(k);}inv.setItem(getConfig().getInt("gui.close-slot",31),item(Material.BARRIER,"&cClose"));gui.put(p.getUniqueId(),"kits");p.openInventory(inv);}
    private void openEditMenu(Player p){Inventory inv=Bukkit.createInventory(null,36,color(getConfig().getString("gui.edit-title","&8Edit Kits Menu")));ConfigurationSection sec=getConfig().getConfigurationSection("kits");int[] slots={10,11,12,13,14,15,16,20,21,22,23,24};int i=0;if(sec!=null)for(String k:sec.getKeys(false)){if(i>=slots.length)break;inv.setItem(slots[i++],icon(k));}inv.setItem(getConfig().getInt("gui.close-slot",31),item(Material.BARRIER,"&cClose"));gui.put(p.getUniqueId(),"edit");p.openInventory(inv);}
    private void openPartyInfo(Player p){Party party=currentParty(p);if(party==null){p.sendMessage(message("&cYou are not in a party."));return;}Inventory inv=Bukkit.createInventory(null,45,color(getConfig().getString("gui.party-info-title","&8Party Info Menu")));int slot=0;for(UUID u:party.members){Player m=Bukkit.getPlayer(u);if(m!=null&&slot<36)inv.setItem(slot++,head(m,"&e"+m.getName(),List.of("&7Party member")));}inv.setItem(38,item(Material.NETHERITE_SWORD,"&c&lRandom Team / Split Match","&7Click to open split kit selection"));inv.setItem(40,item(Material.DIAMOND_SWORD,"&b&lFree-For-All (FFA)","&7Party FFA mode"));gui.put(p.getUniqueId(),"party-info");p.openInventory(inv);}
    private void openPartyFights(Player p){Inventory inv=Bukkit.createInventory(null,36,color(getConfig().getString("gui.party-fights-title","&8Party Fights")));fill(inv);inv.setItem(31,item(Material.BARRIER,"&cClose"));inv.setItem(34,item(Material.DIAMOND,"&c&lSplit"));inv.setItem(35,item(Material.DIAMOND_SWORD,"&b&lFFA"));gui.put(p.getUniqueId(),"party-fights");p.openInventory(inv);}
    private void openSplitMenu(Player p){Party party=currentParty(p);if(party==null||!party.leader.equals(p.getUniqueId())){p.sendMessage(message("&cOnly the party leader can start a split match."));return;}if(party.members.size()<getConfig().getInt("settings.split-min-members",2)){p.sendMessage(message("&cYou need at least 2 party members."));return;}Inventory inv=Bukkit.createInventory(null,36,color(getConfig().getString("gui.split-title","&8Split - Select Kit")));fill(inv);String[] kits={"nethpot","diapot","axe","maceht","macelt","spearmace","smpkit","uhc","crystalffa","opduel","cart","elymace"};int[] slots={10,11,12,13,14,15,16,20,21,22,23,24};for(int i=0;i<kits.length;i++)if(getConfig().contains("kits."+kits[i]))inv.setItem(slots[i],icon(kits[i]));inv.setItem(31,item(Material.BARRIER,"&cClose"));gui.put(p.getUniqueId(),"split");p.openInventory(inv);}

    @EventHandler public void onInventoryClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String g=gui.get(p.getUniqueId());if(g==null)return;e.setCancelled(true);if(e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;int s=e.getRawSlot();if(s==getConfig().getInt("gui.close-slot",31)){p.closeInventory();return;}if(g.equals("kits")){String k=kitAtSlot(s);if(k!=null){p.closeInventory();selectKit(p,k);}}else if(g.equals("edit")){String k=kitAtSlot(s);if(k!=null){p.closeInventory();startEdit(p,k);}}else if(g.equals("party-info")){if(s==38)openSplitMenu(p);else if(s==40){p.closeInventory();p.sendMessage(message("&bParty FFA mode launched. Each member must choose a normal FFA kit from /ffa menu."));for(UUID u:currentParty(p).members){Player m=Bukkit.getPlayer(u);if(m!=null&&kit.get(u)==null)openKitMenu(m);}}}else if(g.equals("split")){String k=kitAtSlot(s);if(k!=null){p.closeInventory();startSplit(p,k);}}else if(g.equals("party-fights")){if(s==34)openSplitMenu(p);}else if(g.equals("spectate")){if(s<27){List<Player> targets=new ArrayList<>();for(Player t:Bukkit.getOnlinePlayers())if(t!=p&&inFFAWorld(t)&&kit.containsKey(t.getUniqueId()))targets.add(t);if(s<targets.size()){Player t=targets.get(s);p.closeInventory();p.setGameMode(GameMode.SPECTATOR);teleport(p,t.getLocation());p.sendMessage(message("&aNow spectating &e"+t.getName()+"&a. Use /ffa back to return."));}}} }
    @EventHandler public void onInventoryDrag(InventoryDragEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String g=gui.get(p.getUniqueId());
        if(g!=null && e.getRawSlots().stream().anyMatch(slot -> slot < e.getView().getTopInventory().getSize())) e.setCancelled(true);
    }

    @EventHandler public void onInventoryMove(InventoryMoveItemEvent e){
        // Selector/GUI inventories are never valid item-transfer targets.
        if(e.getDestination().getHolder() instanceof Player p && gui.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent e){gui.remove(e.getPlayer().getUniqueId());}

    private String kitAtSlot(int slot){int[] slots={10,11,12,13,14,15,16,20,21,22,23,24};if(!contains(slots,slot))return null;ConfigurationSection sec=getConfig().getConfigurationSection("kits");if(sec==null)return null;int idx=0;for(String k:sec.getKeys(false)){if(idx>=slots.length)break;if(slots[idx++]==slot)return k;}return null;}
    private void selectKit(Player p,String k){
        if(!requireFFA(p)||!getConfig().contains("kits."+k))return;
        kit.put(p.getUniqueId(),k);editing.remove(p.getUniqueId());clearCombat(p);clearPlayer(p);
        Location l=location("kits."+k+".arena");if(l==null)l=location("locations.lobby");if(l!=null)teleport(p,l);
        if(usingMysql()&&!mysqlLoadedPlayers.contains(p.getUniqueId())){
            p.sendMessage(msg("storage-loading"));
            preloadMysqlKits(p);
            Bukkit.getScheduler().runTaskLater(this,()->selectKitAfterStorage(p,k),Math.max(1,getConfig().getInt("storage.mysql.selection-retry-ticks",4)));
            return;
        }
        selectKitAfterStorage(p,k);
    }
    private void startEdit(Player p,String k){if(location("locations.edit-kit")==null){p.sendMessage(message("&cEdit-kit location is not configured."));return;}editing.put(p.getUniqueId(),k);clearPlayer(p);runKitCommand(p,k);teleport(p,location("locations.edit-kit"));p.sendMessage(message("&aYou are editing &e"+k+"&a. Use &e/kit save &ato save or &e/kit leave &ato cancel."));}

    @EventHandler public void onRightClick(PlayerInteractEvent e){Player p=e.getPlayer();if(!inFFAWorld(p)||!ffaPlayers.contains(p.getUniqueId())||kit.containsKey(p.getUniqueId())||editing.containsKey(p.getUniqueId()))return;ItemStack it=e.getItem();if(it==null||!it.hasItemMeta()||it.getItemMeta().getDisplayName()==null)return;ItemMeta im=it.getItemMeta();if(selectorKey!=null&&!im.getPersistentDataContainer().has(selectorKey,org.bukkit.persistence.PersistentDataType.BYTE))return;String n=strip(im.getDisplayName());e.setCancelled(true);if(n.contains("FFA Menu"))openKitMenu(p);else if(n.contains("Edit Kits"))openEditMenu(p);else if(n.contains("Spectate"))openSpectate(p);else if(n.equalsIgnoreCase("Party"))openPartyInfo(p);else if(n.contains("Sword Arena"))selectKit(p,"sword");else if(n.contains("Party Info"))openPartyInfo(p);else if(n.contains("Party Chat Toggle")){togglePartyChat(p);}else if(n.contains("Disband / Leave")){if(parties.containsKey(p.getUniqueId()))disbandParty(parties.get(p.getUniqueId()),false);else leaveParty(p);}if(it.getType()==Material.END_CRYSTAL)crystalPlace.put(p.getUniqueId(),System.currentTimeMillis());}

    private void openSpectate(Player p){Inventory inv=Bukkit.createInventory(null,36,color(getConfig().getString("gui.spectate-title","&8Spectate Players")));int s=0;for(Player t:Bukkit.getOnlinePlayers())if(t!=p&&inFFAWorld(t)&&kit.containsKey(t.getUniqueId())&&s<27)inv.setItem(s++,head(t,"&e"+t.getName(),List.of("&7Kit: "+display(kit.get(t.getUniqueId())))));inv.setItem(31,item(Material.BARRIER,"&cClose"));gui.put(p.getUniqueId(),"spectate");p.openInventory(inv);}

    @EventHandler public void onDamage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player victim)||!inFFAWorld(victim))return;Player attacker=resolveAttacker(e);if(attacker!=null){if(sameSplitTeam(attacker,victim)){e.setCancelled(true);return;}lastAttacker.put(victim.getUniqueId(),attacker.getUniqueId());lastHit.put(victim.getUniqueId(),System.currentTimeMillis());combat.put(victim.getUniqueId(),System.currentTimeMillis());combat.put(attacker.getUniqueId(),System.currentTimeMillis());}}
    private Player resolveAttacker(EntityDamageEvent e){if(e instanceof EntityDamageByEntityEvent de){Entity a=de.getDamager();if(a instanceof Player p)return p;if(a instanceof Projectile pr&&pr.getShooter() instanceof Player p)return p;if(a instanceof EnderPearl ep&&ep.getShooter() instanceof Player p)return p;}long now=System.currentTimeMillis();if(e.getEntity() instanceof Player v){Player best=null;double d=Double.MAX_VALUE;for(Player p:Bukkit.getOnlinePlayers()){Long t=crystalPlace.get(p.getUniqueId());if(p!=v&&t!=null&&now-t<getConfig().getLong("settings.crystal-credit-seconds",5)*1000L&&p.getWorld()==v.getWorld()){double dd=p.getLocation().distanceSquared(v.getLocation());if(dd<d){d=dd;best=p;}}}return best;}return null;}
    private boolean sameSplitTeam(Player a,Player b){String x=playerSplit.get(a.getUniqueId()),y=playerSplit.get(b.getUniqueId());if(x==null||!x.equals(y))return false;SplitMatch m=splits.get(x);return m!=null&&m.team.getOrDefault(a.getUniqueId(),0).equals(m.team.getOrDefault(b.getUniqueId(),-1));}

    @EventHandler public void onDeath(PlayerDeathEvent e){
        Player p=e.getEntity();
        if(!inFFAWorld(p))return;
        String world=p.getWorld().getName();
        e.setDeathMessage(color("&c"+p.getName()+" &ewas slain in the FFA arena!"));
        e.getDrops().clear();
        e.setDroppedExp(0);
        p.closeInventory();
        UUID killer=lastAttacker.get(p.getUniqueId());
        Long hit=lastHit.get(p.getUniqueId());
        boolean validKiller=killer!=null&&hit!=null&&System.currentTimeMillis()-hit<getConfig().getLong("settings.killer-credit-seconds",15)*1000L&&(!killer.equals(p.getUniqueId()));
        recordDeath(p.getUniqueId(),world);
        if(validKiller){
            recordKill(killer,world);
            Player k=Bukkit.getPlayer(killer);
            if(k!=null)Bukkit.getScheduler().runTask(this,()->restock(k));
        }
        pendingRecovery.add(p.getUniqueId());
        String deadKit=kit.get(p.getUniqueId());
        if(getConfig().getBoolean("arena-reset.reset-on-kit-death",false)&&deadKit!=null)resetArenaAsync(deadKit);
        kit.remove(p.getUniqueId());
        if(playerSplit.containsKey(p.getUniqueId())){SplitMatch m=splits.get(playerSplit.get(p.getUniqueId()));if(m!=null){Integer team=m.team.get(p.getUniqueId());if(team!=null){respawnLocations.put(p.getUniqueId(),team==1?m.spawnA:m.spawnB);m.alive.put(team,m.alive.getOrDefault(team,1)-1);if(m.alive.get(team)<=0)finishSplit(m);}}}
    }

    private void registerPostRespawn(){if(!getConfig().getBoolean("recovery.use-post-respawn-event",true))return;try{Class<?> c=Class.forName("com.destroystokyo.paper.event.player.PlayerPostRespawnEvent");Object pm=getServer().getPluginManager();Method m=org.bukkit.plugin.PluginManager.class.getMethod("registerEvent",Class.class,org.bukkit.event.Listener.class,EventPriority.class,EventExecutor.class,org.bukkit.plugin.Plugin.class);EventExecutor ex=(listener,event)->{try{Method gp=event.getClass().getMethod("getPlayer");Object o=gp.invoke(event);if(o instanceof Player p)recoverNow(p);}catch(Exception x){getLogger().warning("Post-respawn recovery failed: "+x.getMessage());}};m.invoke(pm,c,this,EventPriority.MONITOR,ex,this);getLogger().info("Paper PlayerPostRespawnEvent registered.");}catch(Throwable t){getLogger().warning("PlayerPostRespawnEvent unavailable; tick fallback remains active: "+t.getClass().getSimpleName());}}

    @EventHandler public void onRespawn(PlayerRespawnEvent e){Player p=e.getPlayer();if(!pendingRecovery.contains(p.getUniqueId())&&!ffaPlayers.contains(p.getUniqueId()))return;pendingRecovery.add(p.getUniqueId());Location l=respawnLocations.remove(p.getUniqueId());if(l==null)l=location("locations.lobby");if(l!=null)e.setRespawnLocation(l);}
    private void recoverNow(Player p){if(!pendingRecovery.contains(p.getUniqueId()))return;pendingRecovery.remove(p.getUniqueId());if(!inFFAWorld(p))return;if(playerSplit.containsKey(p.getUniqueId())){p.setGameMode(GameMode.SPECTATOR);return;}final int[] ticks=getConfig().getIntegerList("recovery.recovery-ticks").stream().mapToInt(Integer::intValue).toArray();if(ticks.length==0){normalize(p);return;}for(int delay:ticks)Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())normalize(p);},Math.max(0,delay));}
    private void normalize(Player p){try{if(getConfig().getBoolean("recovery.reset-sneaking",true))p.setSneaking(false);if(getConfig().getBoolean("recovery.reset-sprinting",true))p.setSprinting(false);if(getConfig().getBoolean("recovery.reset-gliding",true))p.setGliding(false);if(getConfig().getBoolean("recovery.reset-riptiding",true))p.setRiptiding(false);if(getConfig().getBoolean("recovery.reset-flying",true))p.setFlying(false);if(getConfig().getBoolean("recovery.zero-velocity",true))p.setVelocity(new org.bukkit.util.Vector(0,0,0));if(getConfig().getBoolean("recovery.clear-active-item",true))p.clearActiveItem();if(getConfig().getBoolean("recovery.leave-vehicle",true))p.leaveVehicle();try{Class<?> pose=Class.forName("org.bukkit.entity.Pose");Object standing=Enum.valueOf((Class<Enum>)pose,getConfig().getString("recovery.pose","STANDING"));Method m=p.getClass().getMethod("setPose",pose,boolean.class);m.invoke(p,standing,getConfig().getBoolean("recovery.fixed-pose",true));}catch(Throwable ignored){}resendEntityData(p);if(getConfig().getBoolean("recovery.teleport-to-lobby-after-respawn",true)&&pendingRecovery.isEmpty()&&kit.get(p.getUniqueId())==null){Location l=location("locations.lobby");if(l!=null)teleport(p,l);giveSelector(p);}}catch(Throwable t){getLogger().warning("Normalization error: "+t.getMessage());}}
    private void resendEntityData(Player p){if(!getConfig().getBoolean("recovery.resend-entity-data",true))return;try{Object handle=p.getClass().getMethod("getHandle").invoke(p);for(String name:List.of("resendPossiblyDesyncedEntityData","refreshEntityData")){for(Method m:handle.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterCount()==1){m.invoke(handle,handle);return;}}}catch(Throwable t){getLogger().fine("Entity-data resync unavailable: "+t.getMessage());}}

    private void startSplit(Player leader,String k){Party party=currentParty(leader);if(party==null||!party.leader.equals(leader.getUniqueId()))return;int total=party.members.size();int max=getConfig().getInt("split.max-arenas."+k,getConfig().getInt("split.max-arenas-default",10));List<Integer> available=new ArrayList<>();for(int i=1;i<=max;i++)if(getConfig().contains("split.arenas."+k+"."+i+".A")&&getConfig().contains("split.arenas."+k+"."+i+".B")&&!splitArenaLocks.containsKey(k+":"+i))available.add(i);if(available.isEmpty()){leader.sendMessage(message("&cNo available split arena is configured for "+k+"."));return;}int arena=available.get(new Random().nextInt(available.size()));Location A=deserializeLoc(getConfig().getConfigurationSection("split.arenas."+k+"."+arena+".A"));Location B=deserializeLoc(getConfig().getConfigurationSection("split.arenas."+k+"."+arena+".B"));if(A==null||B==null)return;String id=leader.getUniqueId()+"-"+System.nanoTime();SplitMatch m=new SplitMatch(id,k,arena,A,B);List<UUID> members=new ArrayList<>(party.members);Collections.shuffle(members);int aSize=total/2;if(aSize<1)aSize=1;for(int i=0;i<members.size();i++){UUID u=members.get(i);int team=i<aSize?1:2;m.team.put(u,team);m.alive.put(team,m.alive.getOrDefault(team,0)+1);playerSplit.put(u,id);kit.put(u,k);Player p=Bukkit.getPlayer(u);if(p!=null){clearPlayer(p);if(storageMode().equals("mysql")&&!mysqlLoadedPlayers.contains(p.getUniqueId())&&mysqlReady)preloadMysqlKits(p);if(!loadCustomKit(p,k))runKitCommand(p,k);teleport(p,team==1?A:B);normalize(p);p.sendMessage(msg("split-start").replace("{arena}",String.valueOf(arena)).replace("{team}",team==1?"A":"B"));}}splits.put(id,m);splitArenaLocks.put(k+":"+arena,System.currentTimeMillis());}
    private void finishSplit(SplitMatch m){if(m.finished)return;m.finished=true;long delay=getConfig().getLong("settings.split-end-delay-seconds",5)*20L;Bukkit.getScheduler().runTaskLater(this,()->{for(UUID u:m.team.keySet()){Player p=Bukkit.getPlayer(u);playerSplit.remove(u);kit.remove(u);if(p!=null){clearPlayer(p);Location l=location("locations.lobby");if(l!=null)teleport(p,l);giveSelector(p);p.sendMessage(message("&aThe split match has ended! You have been returned to the lobby."));}}splits.remove(m.id);splitArenaLocks.remove(m.kit+":"+m.arena);if(getConfig().getBoolean("arena-reset.reset-on-split-end",true))resetArenaAsync(m.kit+"-"+m.arena);},delay);}
    private void endSplitFor(UUID u){String id=playerSplit.remove(u);if(id==null)return;SplitMatch m=splits.get(id);if(m!=null){m.team.remove(u);if(m.team.isEmpty()){splits.remove(id);splitArenaLocks.remove(m.kit+":"+m.arena);}}}

    private void restock(Player p){String k=kit.get(p.getUniqueId());if(k==null)return;clearPlayer(p);runKitCommand(p,k);normalize(p);p.sendMessage(message("&aRestocked: &e"+display(k)+"&a."));}

    private void startTasks(){
        tickTask=Bukkit.getScheduler().runTaskTimer(this,()->{for(UUID u:new ArrayList<>(pendingRecovery)){Player p=Bukkit.getPlayer(u);if(p!=null&&p.isOnline()&&inFFAWorld(p)&&!p.isDead())recoverNow(p);}for(Player p:Bukkit.getOnlinePlayers())if(inFFAWorld(p)&&combat.containsKey(p.getUniqueId())&&isCombat(p)&&safezoneContains(p)&&!admin(p))pushOut(p);},1,1);
        if(getConfig().getBoolean("clearlag.enabled",true)){long interval=Math.max(20,getConfig().getLong("clearlag.interval-seconds",10)*20L);long warning=Math.max(0,getConfig().getLong("clearlag.warning-seconds",3)*20L);clearTask=Bukkit.getScheduler().runTaskTimer(this,()->{if(warning>0&&warning<interval)Bukkit.getScheduler().runTaskLater(this,()->warnClear(),interval-warning);clearDrops();},interval,interval);}
        long safeTicks=Math.max(1,getConfig().getLong("settings.safezone-check-ticks",2));safezoneTask=Bukkit.getScheduler().runTaskTimer(this,()->{for(Player p:Bukkit.getOnlinePlayers())if(inFFAWorld(p)&&isCombat(p)&&safezoneContains(p)&&!admin(p))pushOut(p);},safeTicks,safeTicks);
    }
    private void warnClear(){World w=Bukkit.getWorld(ffaWorldName());if(w==null)return;for(Player p:w.getPlayers())p.sendActionBar(msg("clear-warning"));}

    private void clearDrops(){World w=Bukkit.getWorld(ffaWorldName());if(w==null)return;for(Entity e:w.getEntities())if(e instanceof Item)e.remove();}

    @EventHandler public void onChat(AsyncPlayerChatEvent e){Player p=e.getPlayer();if(!partyChat.getOrDefault(p.getUniqueId(),false))return;Party party=currentParty(p);if(party==null)return;e.setCancelled(true);for(UUID u:party.members){Player m=Bukkit.getPlayer(u);if(m!=null)m.sendMessage(message("&d[Party Chat] &e"+p.getName()+": &f"+e.getMessage()));}}
    @EventHandler public void onDrop(PlayerDropItemEvent e){Player p=e.getPlayer();if(inFFAWorld(p)&&ffaPlayers.contains(p.getUniqueId())&&kit.get(p.getUniqueId())==null&&!editing.containsKey(p.getUniqueId()))e.setCancelled(true);}
    @EventHandler public void onCommandPreprocess(PlayerCommandPreprocessEvent e){Player p=e.getPlayer();if(!ffaPlayers.contains(p.getUniqueId())||admin(p))return;String c=e.getMessage().substring(1).toLowerCase(Locale.ROOT);String root=c.split(" ")[0];if(Set.of("ffa","party","p","kit","login","register","l","reg").contains(root))return;e.setCancelled(true);p.sendMessage(message("&cYou are currently in FFA! Use &e/ffa back &cto leave safely."));}
    @EventHandler public void onTeleport(PlayerTeleportEvent e){Player p=e.getPlayer();if(!getConfig().getBoolean("server.prevent-leaving-with-kit",true)||!ffaPlayers.contains(p.getUniqueId())||kit.get(p.getUniqueId())==null)return;if(e.getFrom().getWorld()!=null&&e.getTo()!=null&&e.getTo().getWorld()!=e.getFrom().getWorld()&&!admin(p)){e.setCancelled(true);p.sendMessage(message("&cYou cannot leave the FFA world while wearing a kit. Use &e/ffa back&c."));}}

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e){Player p=e.getPlayer();if(getConfig().getBoolean("server.prevent-leaving-with-kit",true)&&ffaPlayers.contains(p.getUniqueId())&&!inFFAWorld(p)){p.sendMessage(message("&cYou are still in FFA. Use &e/ffa back &cto leave safely."));Bukkit.getScheduler().runTask(this,()->{Location l=location("locations.lobby");if(l!=null)teleport(p,l);});}}
    @EventHandler public void onQuit(PlayerQuitEvent e){Player p=e.getPlayer();UUID id=p.getUniqueId();if(inventoryMode().equals("native")&&ffaPlayers.contains(id)){InventorySnapshot snap=snapshots.remove(id);if(snap!=null)snap.restore(p);}gui.remove(id);pendingRecovery.remove(id);kit.remove(id);editing.remove(id);clearCombat(p);Player ignored=p;Party party=parties.get(id);if(party!=null)disbandParty(party,false);else leaveParty(p);ffaPlayers.remove(id);playerSplit.remove(id);}
    @EventHandler public void onJoin(PlayerJoinEvent e){Player p=e.getPlayer();if(usingMysql())preloadMysqlKits(p);if(ffaPlayers.contains(p.getUniqueId())&&inFFAWorld(p))Bukkit.getScheduler().runTaskLater(this,()->{if(kit.get(p.getUniqueId())==null)giveSelector(p);else normalize(p);},2);}

    private void pushOut(Player p){Location a=location("safezone.pos1"),b=location("safezone.pos2");if(a==null||b==null)return;double minX=Math.min(a.getX(),b.getX()),maxX=Math.max(a.getX(),b.getX()),minY=Math.min(a.getY(),b.getY()),maxY=Math.max(a.getY(),b.getY()),minZ=Math.min(a.getZ(),b.getZ()),maxZ=Math.max(a.getZ(),b.getZ());Location l=p.getLocation();if(l.getX()<minX||l.getX()>maxX||l.getY()<minY||l.getY()>maxY||l.getZ()<minZ||l.getZ()>maxZ)return;double dl=l.getX()-minX,dr=maxX-l.getX(),db=l.getZ()-minZ,df=maxZ-l.getZ(),x=l.getX(),z=l.getZ(),d=dl;String side="l";if(dr<d){d=dr;side="r";}if(db<d){d=db;side="b";}if(df<d){side="f";}double push=getConfig().getDouble("safezone.push-distance",1.0);if(side.equals("l"))x=minX-push;else if(side.equals("r"))x=maxX+push;else if(side.equals("b"))z=minZ-push;else z=maxZ+push;Location n=new Location(p.getWorld(),x,l.getY(),z,l.getYaw(),l.getPitch());teleport(p,n);p.sendActionBar(msg("combat-safezone"));}
    private boolean safezoneContains(Player p){Location a=location("safezone.pos1"),b=location("safezone.pos2");if(a==null||b==null||p.getWorld()!=a.getWorld())return false;Location l=p.getLocation();return l.getX()>=Math.min(a.getX(),b.getX())&&l.getX()<=Math.max(a.getX(),b.getX())&&l.getY()>=Math.min(a.getY(),b.getY())&&l.getY()<=Math.max(a.getY(),b.getY())&&l.getZ()>=Math.min(a.getZ(),b.getZ())&&l.getZ()<=Math.max(a.getZ(),b.getZ());}

    private void giveSelector(Player p){clearPlayer(p);Party party=currentParty(p);if(party!=null){p.getInventory().setItem(0,item(Material.NETHERITE_SWORD,"&6Party Info &7(Right Click)","&7Click to view party info"));p.getInventory().setItem(4,item(Material.PAPER,"&eParty Chat Toggle &7(Right Click)","&7Click to toggle party chat mode"));p.getInventory().setItem(8,item(Material.REDSTONE,"&cDisband / Leave Party &7(Right Click)","&7Click to disband or leave your party"));}else{p.getInventory().setItem(0,item(Material.NETHERITE_SWORD,"&6FFA Menu &7(Right Click)"));p.getInventory().setItem(1,item(Material.BOOK,"&eEdit Kits &7(Right Click)"));p.getInventory().setItem(3,item(Material.ENDER_EYE,"&bSpectate &7(Right Click)"));p.getInventory().setItem(4,item(Material.FIREWORK_ROCKET,"&dParty &7(Right Click)"));if(getConfig().contains("kits.sword"))p.getInventory().setItem(5,item(Material.DIAMOND_SWORD,"&b&lSword Arena &7(Right Click)","&7Instant teleport + Sword Kit"));}}

    private ItemStack icon(String k){String mat=getConfig().getString("kits."+k+".icon","DIAMOND_SWORD");ItemStack i=item(Material.matchMaterial(mat)==null?Material.DIAMOND_SWORD:Material.matchMaterial(mat),getConfig().getString("kits."+k+".display","&e"+k),"&7Click to select this kit");ItemMeta m=i.getItemMeta();if(m!=null&&kitKey!=null)m.getPersistentDataContainer().set(kitKey,org.bukkit.persistence.PersistentDataType.STRING,k);i.setItemMeta(m);return i;}
    private ItemStack head(Player p,String name,List<String> lore){ItemStack i=new ItemStack(Material.PLAYER_HEAD);ItemMeta m=i.getItemMeta();m.setDisplayName(color(name));m.setLore(colorList(lore));i.setItemMeta(m);return i;}
    private ItemStack item(Material m,String name,String... lore){
        ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.setDisplayName(color(name));if(lore.length>0)meta.setLore(colorList(Arrays.asList(lore)));
        if(meta!=null && selectorKey!=null && (strip(name).contains("FFA Menu")||strip(name).contains("Edit Kits")||strip(name).contains("Spectate")||strip(name).contains("Party")||strip(name).contains("Sword Arena")||strip(name).contains("Disband / Leave"))) meta.getPersistentDataContainer().set(selectorKey,org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
        i.setItemMeta(meta);return i;
    }
    private void fill(Inventory inv){ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=0;i<inv.getSize();i++)inv.setItem(i,pane);}

    private void runKitCommand(Player p,String k){String cmd=getConfig().getString("kits."+k+".give.command");if(cmd==null||cmd.isBlank())return;cmd=cmd.replace("{player}",p.getName()).replace("{uuid}",p.getUniqueId().toString());Bukkit.dispatchCommand(Bukkit.getConsoleSender(),cmd);}
    private void saveCustomKit(Player p,String k){
        try{
            CustomKitData data=new CustomKitData(serializeItems(p.getInventory().getContents()),serializeItems(p.getInventory().getArmorContents()),serializeItem(p.getInventory().getItemInOffHand()));
            String key=customKey(p.getUniqueId(),k);
            customKitCache.put(key,data);
            if(usingMysql()){
                storageExecutor.submit(()->saveMysqlKit(p.getUniqueId(),k,data));
            }else{
                customKits.set(p.getUniqueId()+"."+k+".inventory",data.inventory);
                customKits.set(p.getUniqueId()+"."+k+".armor",data.armor);
                customKits.set(p.getUniqueId()+"."+k+".offhand",data.offhand);
                customKits.save(customKitsFile.toFile());
            }
        }catch(Exception e){getLogger().warning("Could not save custom kit: "+e.getMessage());}
    }

    private boolean loadCustomKit(Player p,String k){
        CustomKitData data=customKitCache.get(customKey(p.getUniqueId(),k));
        if(data!=null){applyCustomKit(p,data);return true;}
        if(usingMysql()) return false;
        try{
            String base=p.getUniqueId()+"."+k;
            if(!customKits.contains(base+".inventory"))return false;
            data=new CustomKitData(customKits.getStringList(base+".inventory"),customKits.getStringList(base+".armor"),customKits.getString(base+".offhand",""));
            customKitCache.put(customKey(p.getUniqueId(),k),data);
            applyCustomKit(p,data);return true;
        }catch(Exception e){return false;}
    }

    private String serializeItem(ItemStack i)throws IOException{if(i==null||i.getType()==Material.AIR)return "";return Base64.getEncoder().encodeToString(serializeObject(i));}
    private List<String> serializeItems(ItemStack[] arr)throws IOException{List<String> out=new ArrayList<>();for(ItemStack i:arr)out.add(serializeItem(i));return out;}
    private byte[] serializeObject(Object o)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();try(BukkitObjectOutputStream x=new BukkitObjectOutputStream(b)){x.writeObject(o);}return b.toByteArray();}
    private ItemStack deserializeItem(String s)throws IOException,ClassNotFoundException{if(s==null||s.isEmpty())return new ItemStack(Material.AIR);try(BukkitObjectInputStream x=new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(s)))){return (ItemStack)x.readObject();}}
    private ItemStack[] deserializeItems(List<String> l)throws IOException,ClassNotFoundException{ItemStack[] a=new ItemStack[l.size()];for(int i=0;i<l.size();i++)a[i]=deserializeItem(l.get(i));return a;}

    private void applyCustomKit(Player p,CustomKitData data){
        try{p.getInventory().setContents(deserializeItems(data.inventory));p.getInventory().setArmorContents(deserializeItems(data.armor));p.getInventory().setItemInOffHand(deserializeItem(data.offhand));}catch(Exception e){getLogger().warning("Could not apply custom kit: "+e.getMessage());}
    }

    private String customKey(UUID u,String k){return u.toString()+":"+k.toLowerCase(Locale.ROOT);}
    private String storageMode(){return getConfig().getString("storage.mode","yaml").toLowerCase(Locale.ROOT);}
    private boolean usingMysql(){return storageMode().equals("mysql")&&mysqlReady;}

    private void loadCustomKits(){
        try{if(!Files.exists(customKitsFile)){customKits=new YamlConfiguration();return;}customKits=YamlConfiguration.loadConfiguration(customKitsFile.toFile());}catch(Exception e){customKits=new YamlConfiguration();}
    }
    private void saveCustomKits(){if(storageMode().equals("mysql"))return;try{customKits.save(customKitsFile.toFile());}catch(Exception ignored){}}

    private void initStorageAsync(){
        if(!storageMode().equals("mysql")){getLogger().info("Storage: YAML (local custom-kits.yml)");return;}
        storageExecutor.submit(()->{
            try(Connection c=mysqlConnection(); Statement st=c.createStatement()){
                st.executeUpdate("CREATE TABLE IF NOT EXISTS `"+mysqlTable()+"` (uuid CHAR(36) NOT NULL, kit VARCHAR(64) NOT NULL, inventory MEDIUMTEXT NOT NULL, armor MEDIUMTEXT NOT NULL, offhand MEDIUMTEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (uuid, kit)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                mysqlReady=true;
                getLogger().info("MySQL storage connected and schema verified.");
                for(Player p:Bukkit.getOnlinePlayers()) preloadMysqlKits(p);
            }catch(Exception e){
                mysqlReady=false;
                getLogger().severe("MySQL storage could not connect: "+e.getMessage());
                if(getConfig().getBoolean("storage.mysql.fallback-to-yaml",true))getLogger().warning("Falling back to YAML storage for this session.");
            }
        });
    }

    private String mysqlTable(){
        String prefix=getConfig().getString("storage.mysql.table-prefix","orionffa_");
        prefix=prefix==null?"orionffa_":prefix.replaceAll("[^A-Za-z0-9_]","");
        if(prefix.isEmpty())prefix="orionffa_";
        return prefix+"custom_kits";
    }

    private Connection mysqlConnection() throws SQLException{
        String host=getConfig().getString("storage.mysql.host","127.0.0.1");
        int port=getConfig().getInt("storage.mysql.port",3306);
        String database=getConfig().getString("storage.mysql.database","minecraft");
        String user=getConfig().getString("storage.mysql.username","root");
        String pass=getConfig().getString("storage.mysql.password","");
        String params="useSSL="+getConfig().getBoolean("storage.mysql.use-ssl",false)+"&characterEncoding=utf8&serverTimezone=UTC&tcpKeepAlive=true";
        try{
            return DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/"+database+"?"+params,user,pass);
        }catch(SQLException first){
            if(!getConfig().getBoolean("storage.mysql.create-database",true))throw first;
            try(Connection root=DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/?"+params,user,pass);Statement st=root.createStatement()){
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS `"+database.replace("`","")+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            return DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/"+database+"?"+params,user,pass);
        }
    }

    private void preloadMysqlKits(Player p){
        if(!storageMode().equals("mysql")||!mysqlReady)return;
        UUID u=p.getUniqueId();
        if(mysqlLoadedPlayers.contains(u)||!mysqlLoadingPlayers.add(u))return;
        storageExecutor.submit(()->{
            try(Connection c=mysqlConnection(); PreparedStatement ps=c.prepareStatement("SELECT kit, inventory, armor, offhand FROM `"+mysqlTable()+"` WHERE uuid=?")){
                ps.setString(1,u.toString());
                try(ResultSet rs=ps.executeQuery()){while(rs.next())customKitCache.put(customKey(u,rs.getString(1)),new CustomKitData(splitBlob(rs.getString(2)),splitBlob(rs.getString(3)),rs.getString(4)));}
                mysqlLoadedPlayers.add(u);
            }catch(Exception e){getLogger().warning("Could not preload MySQL kits for "+p.getName()+": "+e.getMessage());}
            finally{mysqlLoadingPlayers.remove(u);}
        });
    }

    private void saveMysqlKit(UUID u,String k,CustomKitData data){
        if(!mysqlReady){if(getConfig().getBoolean("storage.mysql.fallback-to-yaml",true))saveYamlKit(u,k,data);return;}
        try(Connection c=mysqlConnection(); PreparedStatement ps=c.prepareStatement("INSERT INTO `"+mysqlTable()+"` (uuid,kit,inventory,armor,offhand) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE inventory=VALUES(inventory), armor=VALUES(armor), offhand=VALUES(offhand)")){
            ps.setString(1,u.toString());ps.setString(2,k);ps.setString(3,joinBlob(data.inventory));ps.setString(4,joinBlob(data.armor));ps.setString(5,data.offhand==null?"":data.offhand);ps.executeUpdate();
        }catch(Exception e){getLogger().warning("MySQL kit save failed for "+u+"/"+k+": "+e.getMessage());if(getConfig().getBoolean("storage.mysql.fallback-to-yaml",true))saveYamlKit(u,k,data);}
    }

    private void saveYamlKit(UUID u,String k,CustomKitData data){
        Bukkit.getScheduler().runTask(this,()->{try{String b=u+"."+k;customKits.set(b+".inventory",data.inventory);customKits.set(b+".armor",data.armor);customKits.set(b+".offhand",data.offhand);customKits.save(customKitsFile.toFile());}catch(Exception e){getLogger().warning("YAML fallback save failed: "+e.getMessage());}});
    }

    private String joinBlob(List<String> list){return String.join("\n",list);}
    private List<String> splitBlob(String blob){return blob==null||blob.isEmpty()?new ArrayList<>():new ArrayList<>(Arrays.asList(blob.split("\n",-1)));}

    private void selectKitAfterStorage(Player p,String k){
        if(!p.isOnline())return;
        if(!loadCustomKit(p,k))runKitCommand(p,k);
        normalize(p);
        p.sendMessage(msg("kit-selected").replace("{kit}",display(k)));
    }

    private String statsFileName(){return getDataFolder()+File.separator+"stats.yml";}
    private String statsKey(UUID u,String world){return u+"|"+world.toLowerCase(Locale.ROOT);}
    private StatsData getStats(UUID u,String world){
        String key=statsKey(u,world);
        StatsData cached=statsCache.get(key);
        if(cached!=null)return cached;
        if(usingMysql()) loadStatsAsync(u,world);
        StatsData yaml=loadStatsYaml(u,world);
        if(yaml!=null){statsCache.putIfAbsent(key,yaml);return statsCache.get(key);}
        return statsCache.computeIfAbsent(key,k->new StatsData());
    }
    private void recordKill(UUID u,String world){
        if(!getConfig().getBoolean("statistics.enabled",true))return;
        String key=statsKey(u,world); StatsData st=statsCache.computeIfAbsent(key,k->new StatsData());
        synchronized(st){st.kills++;st.streak++;if(st.streak>st.bestStreak)st.bestStreak=st.streak;double kd=st.kd();if(st.kills>=getConfig().getLong("statistics.best-kd-minimum-kills",1)&&kd>st.bestKd)st.bestKd=kd;}
        persistStatsAsync(u,world,st);
    }
    private void recordDeath(UUID u,String world){
        if(!getConfig().getBoolean("statistics.enabled",true))return;
        String key=statsKey(u,world); StatsData st=statsCache.computeIfAbsent(key,k->new StatsData());
        synchronized(st){st.deaths++;st.streak=0;double kd=st.kd();if(st.kills>=getConfig().getLong("statistics.best-kd-minimum-kills",1)&&kd>st.bestKd)st.bestKd=kd;}
        persistStatsAsync(u,world,st);
    }
    private String formatKd(double d){return String.format(Locale.US,"%.2f",d);}
    private void loadStatsAsync(UUID u,String world){
        String key=statsKey(u,world); if(!statsLoading.add(key))return;
        storageExecutor.submit(()->{try(Connection c=mysqlConnection();PreparedStatement ps=c.prepareStatement("SELECT kills,deaths,streak,best_streak,best_kd FROM `"+mysqlStatsTable()+"` WHERE uuid=? AND world=?")){ps.setString(1,u.toString());ps.setString(2,world);try(ResultSet rs=ps.executeQuery()){if(rs.next())statsCache.putIfAbsent(key,new StatsData(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getDouble(5)));}}catch(Exception ex){getLogger().warning("Could not load stats for "+u+"/"+world+": "+ex.getMessage());}finally{statsLoading.remove(key);}});
    }
    private void persistStatsAsync(UUID u,String world,StatsData st){
        if(usingMysql()){ StatsData copy=st.copy(); storageExecutor.submit(()->saveStatsMysql(u,world,copy)); }
        else Bukkit.getScheduler().runTaskAsynchronously(this,this::saveStatsYaml);
    }
    private void saveStatsMysql(UUID u,String world,StatsData st){try(Connection c=mysqlConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO `"+mysqlStatsTable()+"` (uuid,world,kills,deaths,streak,best_streak,best_kd) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE kills=VALUES(kills),deaths=VALUES(deaths),streak=VALUES(streak),best_streak=VALUES(best_streak),best_kd=VALUES(best_kd)")){ps.setString(1,u.toString());ps.setString(2,world);ps.setLong(3,st.kills);ps.setLong(4,st.deaths);ps.setLong(5,st.streak);ps.setLong(6,st.bestStreak);ps.setDouble(7,st.bestKd);ps.executeUpdate();}catch(Exception ex){getLogger().warning("MySQL stats save failed for "+u+"/"+world+": "+ex.getMessage());}}
    private void saveStatsYaml(){
        File f=new File(statsFileName());YamlConfiguration y=new YamlConfiguration();
        for(Map.Entry<String,StatsData> e:statsCache.entrySet()){String[] parts=e.getKey().split("\\|",2);if(parts.length!=2)continue;StatsData st=e.getValue();String b=parts[0]+"."+parts[1];y.set(b+".kills",st.kills);y.set(b+".deaths",st.deaths);y.set(b+".streak",st.streak);y.set(b+".best-streak",st.bestStreak);y.set(b+".best-kd",st.bestKd);}
        try{y.save(f);}catch(Exception ex){getLogger().warning("Could not save stats.yml: "+ex.getMessage());}
    }
    private StatsData loadStatsYaml(UUID u,String world){File f=new File(statsFileName());if(!f.exists())return null;YamlConfiguration y=YamlConfiguration.loadConfiguration(f);String b=u+"."+world.toLowerCase(Locale.ROOT);if(!y.contains(b+".kills"))return null;return new StatsData(y.getLong(b+".kills"),y.getLong(b+".deaths"),y.getLong(b+".streak"),y.getLong(b+".best-streak"),y.getDouble(b+".best-kd"));}
    private String mysqlStatsTable(){String p=getConfig().getString("storage.mysql.table-prefix","orionffa_").replaceAll("[^A-Za-z0-9_]","");return (p.isEmpty()?"orionffa_":p)+"stats";}
    private void initStatsTable(){if(!storageMode().equals("mysql"))return;storageExecutor.submit(()->{try(Connection c=mysqlConnection();Statement st=c.createStatement()){st.executeUpdate("CREATE TABLE IF NOT EXISTS `"+mysqlStatsTable()+"` (uuid CHAR(36) NOT NULL, world VARCHAR(128) NOT NULL, kills BIGINT NOT NULL DEFAULT 0, deaths BIGINT NOT NULL DEFAULT 0, streak BIGINT NOT NULL DEFAULT 0, best_streak BIGINT NOT NULL DEFAULT 0, best_kd DOUBLE NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY(uuid,world), INDEX idx_world_kills(world,kills)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");}catch(Exception ex){getLogger().warning("Could not initialize MySQL stats table: "+ex.getMessage());}});}
    private void registerPlaceholderAPI(){
        if(!getConfig().getBoolean("placeholders.enabled",true)||getServer().getPluginManager().getPlugin("PlaceholderAPI")==null){getLogger().info("PlaceholderAPI not detected; placeholders disabled.");return;}
        try{placeholderExpansion=new OrionPlaceholderExpansion();placeholderExpansion.register();getLogger().info("PlaceholderAPI expansion registered: %"+getConfig().getString("placeholders.identifier","orionffa")+"_%");}catch(Throwable t){getLogger().warning("Could not register PlaceholderAPI expansion: "+t.getMessage());}
    }

    private String resolveWorldName(String suffix){
        String wanted=suffix.toLowerCase(Locale.ROOT);
        World exact=Bukkit.getWorld(suffix);if(exact!=null)return exact.getName();
        for(World w:Bukkit.getWorlds()) if(w.getName().toLowerCase(Locale.ROOT).replace(' ','_').equals(wanted)) return w.getName();
        return null;
    }

    public final class OrionPlaceholderExpansion extends PlaceholderExpansion {
        @Override public String getIdentifier(){return getConfig().getString("placeholders.identifier","orionffa");}
        @Override public String getAuthor(){return "Karlow";}
        @Override public String getVersion(){return FFACorePlugin.this.getDescription().getVersion();}
        @Override public boolean persist(){return true;}
        @Override public boolean canRegister(){return true;}
        @Override public String onPlaceholderRequest(Player p,String params){
            if(p==null)return "";String raw=params==null?"":params.toLowerCase(Locale.ROOT);String world=p.getWorld().getName();
            String explicit=null;String base=raw;String[] prefixes={"bestkillstreak_","killstreak_","bestkd_","deaths_","kills_","kd_"};for(String pref:prefixes){if(raw.startsWith(pref)&&raw.length()>pref.length()){String suffix=raw.substring(pref.length());String resolved=resolveWorldName(suffix);if(resolved!=null){explicit=resolved;base=pref.substring(0,pref.length()-1);break;}}}
            StatsData st=getStats(p.getUniqueId(),explicit==null?world:explicit);
            return switch(base){case "kills"->String.valueOf(st.kills);case "deaths"->String.valueOf(st.deaths);case "killstreak","streak"->String.valueOf(st.streak);case "bestkillstreak","beststreak"->String.valueOf(st.bestStreak);case "kd"->formatKd(st.kd());case "bestkd"->formatKd(st.bestKd);case "world"->world;case "world_safe"->world.replace(' ','_').toLowerCase(Locale.ROOT);default->null;};
        }
    }

    private static final class StatsData {long kills,deaths,streak,bestStreak;double bestKd;StatsData(){}StatsData(long k,long d,long s,long bs,double bk){kills=k;deaths=d;streak=s;bestStreak=bs;bestKd=bk;}double kd(){return deaths<=0?kills:(double)kills/deaths;}StatsData copy(){return new StatsData(kills,deaths,streak,bestStreak,bestKd);}}

    private void saveLocation(String path,Location l){getConfig().set(path,serializeLoc(l));saveConfig();}
    private Map<String,Object> serializeLoc(Location l){Map<String,Object> m=new LinkedHashMap<>();m.put("world",l.getWorld().getName());m.put("x",l.getX());m.put("y",l.getY());m.put("z",l.getZ());m.put("yaw",l.getYaw());m.put("pitch",l.getPitch());return m;}
    private Location location(String path){ConfigurationSection s=getConfig().getConfigurationSection(path);if(s==null||s.getString("world")==null)return null;World w=Bukkit.getWorld(s.getString("world"));if(w==null)return null;return new Location(w,s.getDouble("x"),s.getDouble("y"),s.getDouble("z"),(float)s.getDouble("yaw"),(float)s.getDouble("pitch"));}
    private Location deserializeLoc(ConfigurationSection s){if(s==null)return null;World w=Bukkit.getWorld(s.getString("world"));if(w==null)return null;return new Location(w,s.getDouble("x"),s.getDouble("y"),s.getDouble("z"),(float)s.getDouble("yaw"),(float)s.getDouble("pitch"));}

    private String ffaWorldName(){return getConfig().getString("server.ffa-world","ffa");}
    private boolean inFFAWorld(Player p){return p.getWorld()!=null&&p.getWorld().getName().equals(ffaWorldName());}
    private String inventoryMode(){return getConfig().getString("inventory.mode","multiverse-inventories").toLowerCase(Locale.ROOT);}
    private boolean requireFFA(Player p){if(!inFFAWorld(p)){p.sendMessage(msg("must-world"));return false;}if(!ffaPlayers.contains(p.getUniqueId())){p.sendMessage(msg("must-join"));return false;}return true;}
    private boolean admin(Player p){if(p.hasPermission(getConfig().getString("server.admin-permission","ffa.admin"))||p.hasPermission("orionffa.admin"))return true;p.sendMessage(msg("no-permission"));return false;}
    private boolean isCombat(Player p){Long t=combat.get(p.getUniqueId());return t!=null&&System.currentTimeMillis()-t<getConfig().getLong("settings.combat-tag-seconds",10)*1000L;}
    private void clearCombat(Player p){combat.remove(p.getUniqueId());lastAttacker.remove(p.getUniqueId());lastHit.remove(p.getUniqueId());}
    private void clearPlayer(Player p){p.getInventory().clear();p.getInventory().setArmorContents(new ItemStack[4]);p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));p.setHealth(Math.min(p.getMaxHealth(),20));p.setFoodLevel(20);p.setSaturation(20);p.setExp(0);p.setLevel(0);for(PotionEffect pe:new ArrayList<>(p.getActivePotionEffects()))p.removePotionEffect(pe.getType());}
    private void teleport(Player p,Location l){if(l!=null&&l.getWorld()!=null)p.teleport(l);}
    private Party getOrCreateParty(Player p){Party party=parties.get(p.getUniqueId());if(party!=null)return party;UUID leader=p.getUniqueId();party=new Party(leader);party.members.add(leader);parties.put(leader,party);partyMembership.put(leader,leader);return party;}
    private Party currentParty(Player p){UUID leader=partyMembership.get(p.getUniqueId());return leader==null?null:parties.get(leader);}
    private void leaveParty(Player p){Party party=currentParty(p);if(party==null)return;party.members.remove(p.getUniqueId());partyMembership.remove(p.getUniqueId());if(party.leader.equals(p.getUniqueId())){if(party.members.isEmpty())parties.remove(party.leader);else{UUID n=party.members.iterator().next();parties.remove(party.leader);party.leader=n;parties.put(n,party);for(UUID u:party.members)partyMembership.put(u,n);}}updateSelector(p);}
    private void disbandParty(Party party,boolean msg){for(UUID u:new HashSet<>(party.members)){partyMembership.remove(u);Player p=Bukkit.getPlayer(u);if(p!=null){updateSelector(p);if(msg&&p.getUniqueId()!=party.leader)p.sendMessage(message("&cYour party has been disbanded."));}}parties.remove(party.leader);}
    private void updateSelector(Player p){if(inFFAWorld(p)&&ffaPlayers.contains(p.getUniqueId())&&kit.get(p.getUniqueId())==null)giveSelector(p);}
    private int maxParty(){return getConfig().getInt("settings.party-max-members",10);}
    private String display(String k){return strip(getConfig().getString("kits."+k+".display",k));}
    private String msg(String k){
        String raw=getConfig().getString("messages."+k,k);
        String prefix=getConfig().getString("messages.prefix","");
        if(!k.equals("prefix") && getConfig().getBoolean("messages.use-prefix",true)) raw=prefix+raw;
        return message(raw);
    }
    private String message(String s){
        if(s==null)return "";
        return colorCodes(smallCaps(s));
    }
    private String color(String s){return colorCodes(s);}
    private String colorCodes(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
    private String smallCaps(String s){
        if(!getConfig().getBoolean("messages.small-caps.enabled",true))return s;
        StringBuilder out=new StringBuilder();
        boolean placeholder=false, command=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='&'&&i+1<s.length()){out.append(c).append(s.charAt(++i));continue;}
            if(c=='{' ){placeholder=true;out.append(c);continue;}
            if(c=='}'&&placeholder){placeholder=false;out.append(c);continue;}
            if(c=='/'&&getConfig().getBoolean("messages.small-caps.skip-commands",true)){command=true;out.append(c);continue;}
            if(command && Character.isWhitespace(c)){command=false;out.append(c);continue;}
            if(placeholder||command){out.append(c);continue;}
            out.append(smallCap(Character.toLowerCase(c)));
        }
        return out.toString();
    }
    private char smallCap(char c){return switch(c){case 'a'->'ᴀ';case 'b'->'ʙ';case 'c'->'ᴄ';case 'd'->'ᴅ';case 'e'->'ᴇ';case 'f'->'ꜰ';case 'g'->'ɢ';case 'h'->'ʜ';case 'i'->'ɪ';case 'j'->'ᴊ';case 'k'->'ᴋ';case 'l'->'ʟ';case 'm'->'ᴍ';case 'n'->'ɴ';case 'o'->'ᴏ';case 'p'->'ᴘ';case 'q'->'q';case 'r'->'ʀ';case 's'->'s';case 't'->'ᴛ';case 'u'->'ᴜ';case 'v'->'ᴠ';case 'w'->'ᴡ';case 'x'->'x';case 'y'->'ʏ';case 'z'->'ᴢ';default->c;};}
    private List<String> colorList(List<String> x){List<String> o=new ArrayList<>();for(String s:x)o.add(color(s));return o;}
    private String strip(String s){return ChatColor.stripColor(color(s));}
    private boolean contains(int[] a,int x){for(int i:a)if(i==x)return true;return false;}

    private void startArenaResetScheduler(){
        if(arenaResetTask!=null){arenaResetTask.cancel();arenaResetTask=null;}
        if(!getConfig().getBoolean("arena-reset.enabled",true)||!getConfig().getBoolean("arena-reset.schedule.enabled",true))return;
        long interval=Math.max(20L,getConfig().getLong("arena-reset.schedule.interval-seconds",300L)*20L);
        long initial=getConfig().getBoolean("arena-reset.schedule.run-on-startup",false)?1L:interval;
        arenaResetTask=Bukkit.getScheduler().runTaskTimer(this,this::runScheduledArenaResets,initial,interval);
    }

    private void runScheduledArenaResets(){
        ConfigurationSection sec=getConfig().getConfigurationSection("arena-reset.arenas");
        if(sec==null)return;
        for(String id:sec.getKeys(false)){
            ConfigurationSection entry=sec.getConfigurationSection(id);
            if(entry==null||!entry.getBoolean("enabled",true)||!entry.getBoolean("scheduled",true))continue;
            resetArenaAsync(id);
        }
    }

    private void saveArenaFromSelectionAsync(Player p,String id){
        if(getServer().getPluginManager().getPlugin("FastAsyncWorldEdit")==null && getServer().getPluginManager().getPlugin("WorldEdit")==null){
            p.sendMessage(message("&cFastAsyncWorldEdit/WorldEdit is not installed."));return;
        }
        if(!id.matches("[A-Za-z0-9._-]+")){p.sendMessage(message("&cInvalid arena id. Use letters, numbers, '.', '_' or '-'."));return;}
        try{
            Class<?> weC=Class.forName("com.sk89q.worldedit.WorldEdit");
            Object we=weC.getMethod("getInstance").invoke(null);
            Class<?> adapterC=Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object actor=adapterC.getMethod("adapt",Player.class).invoke(null,p);
            Object manager=weC.getMethod("getSessionManager").invoke(we);
            Object session=manager.getClass().getMethod("get",Class.forName("com.sk89q.worldedit.session.SessionOwner")).invoke(manager,actor);
            Object selectionWorld=session.getClass().getMethod("getSelectionWorld").invoke(session);
            if(selectionWorld==null)throw new IllegalStateException("No WorldEdit selection world is set.");
            Object region;
            try{region=session.getClass().getMethod("getSelection",Class.forName("com.sk89q.worldedit.world.World")).invoke(session,selectionWorld);}
            catch(InvocationTargetException ex){throw new IllegalStateException("Make a complete WorldEdit selection first (use //wand, //pos1 and //pos2).",ex.getCause());}
            Object min=region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max=region.getClass().getMethod("getMaximumPoint").invoke(region);
            File folder=new File(getDataFolder(),getConfig().getString("arena-reset.schematic-folder","schematics"));
            if(!folder.exists()&&!folder.mkdirs())throw new IOException("Could not create schematic folder: "+folder);
            File out=new File(folder,id+".schem");
            Location target=new Location(p.getWorld(),number(min,"x"),number(min,"y"),number(min,"z"),0f,0f);
            p.sendMessage(message("&aSelection accepted for &e"+id+"&a. Schematic save queued; this may take a moment for large selections."));
            resetExecutor.submit(()->{
                try{
                    saveSelectionAsSchematic(region,selectionWorld,out);
                    Bukkit.getScheduler().runTask(this,()->{
                        getConfig().set("arena-reset.arenas."+id+".enabled",true);
                        getConfig().set("arena-reset.arenas."+id+".scheduled",getConfig().getBoolean("arena-reset.defaults.scheduled",true));
                        getConfig().set("arena-reset.arenas."+id+".schematic",out.getName());
                        getConfig().set("arena-reset.arenas."+id+".target",serializeLoc(target));
                        saveConfig();
                        p.sendMessage(message("&aSaved arena schematic: &e"+out.getName()+" &7(origin/target = selection minimum point)."));
                    });
                }catch(Exception e){Bukkit.getScheduler().runTask(this,()->p.sendMessage(message("&cArena schematic save failed: &7"+e.getMessage())));getLogger().warning("Arena schematic save failed for "+id+": "+e.getMessage());}
            });
        }catch(Throwable t){p.sendMessage(message("&cCould not read your WorldEdit/FAWE selection: &7"+t.getMessage()));}
    }

    private double number(Object vector,String axis)throws Exception{return ((Number)vector.getClass().getMethod("get"+axis.toUpperCase(Locale.ROOT)).invoke(vector)).doubleValue();}

    private void saveSelectionAsSchematic(Object region,Object selectionWorld,File out)throws Exception{
        Class<?> weWorldC=Class.forName("com.sk89q.worldedit.world.World");
        Class<?> clipboardC=Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard");
        Class<?> blockArrayC=Class.forName("com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard");
        Object clipboard=blockArrayC.getConstructor(Class.forName("com.sk89q.worldedit.regions.Region")).newInstance(region);
        Object min=region.getClass().getMethod("getMinimumPoint").invoke(region);
        Class<?> editSessionC=Class.forName("com.sk89q.worldedit.EditSession");
        Class<?> extentC=Class.forName("com.sk89q.worldedit.extent.Extent");
        Class<?> worldC=Class.forName("com.sk89q.worldedit.world.World");
        Class<?> forwardC=Class.forName("com.sk89q.worldedit.function.operation.ForwardExtentCopy");
        Object copy=forwardC.getConstructor(extentC,Class.forName("com.sk89q.worldedit.regions.Region"),extentC,Class.forName("com.sk89q.worldedit.math.BlockVector3")).newInstance(selectionWorld,region,clipboard,min);
        try{forwardC.getMethod("setCopyingEntities",boolean.class).invoke(copy,getConfig().getBoolean("arena-reset.operations.copy-entities",false));}catch(NoSuchMethodException ignored){}
        Class<?> opsC=Class.forName("com.sk89q.worldedit.function.operation.Operations");
        opsC.getMethod("complete",Class.forName("com.sk89q.worldedit.function.operation.Operation")).invoke(null,copy);
        Class<?> formatsC=Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
        Class<?> builtInC;
        Object format;
        try{builtInC=Class.forName("com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat");format=Enum.valueOf((Class<Enum>)builtInC,"SPONGE_SCHEMATIC");}
        catch(Throwable ignored){format=formatsC.getMethod("findByAlias",String.class).invoke(null,"sponge");}
        try(OutputStream os=new FileOutputStream(out)){Object writer=format.getClass().getMethod("getWriter",OutputStream.class).invoke(format,os);try{writer.getClass().getMethod("write",clipboardC).invoke(writer,clipboard);}finally{try{writer.getClass().getMethod("close").invoke(writer);}catch(Exception ignored){}}}
    }

    private void saveArenaReset(String id,File schematic,Location target){getConfig().set("arena-reset.arenas."+id+".enabled",true);getConfig().set("arena-reset.arenas."+id+".schematic",schematic.getName());getConfig().set("arena-reset.arenas."+id+".target",serializeLoc(target));saveConfig();}
    public void resetArenaAsync(String id){
        if(!getConfig().getBoolean("arena-reset.enabled",true))return;
        if(splitArenaLocks.containsKey("RESET:"+id)){getLogger().warning("Arena reset already running: "+id);return;}
        ConfigurationSection s=getConfig().getConfigurationSection("arena-reset.arenas."+id);
        if(s==null||!s.getBoolean("enabled",true)){getLogger().warning("No enabled arena-reset entry: "+id);return;}
        String fileName=s.getString("schematic");
        Location target=deserializeLoc(s.getConfigurationSection("target"));
        if(fileName==null||target==null){getLogger().warning("Arena reset entry is incomplete: "+id);return;}
        File f=new File(getDataFolder(),getConfig().getString("arena-reset.schematic-folder","schematics")+File.separator+fileName);
        splitArenaLocks.put("RESET:"+id,System.currentTimeMillis());
        Runnable operation=()->{try{if(!f.exists())throw new FileNotFoundException(f.toString());fawePaste(f,target);getLogger().info("Arena reset completed: "+id);}catch(Exception e){getLogger().warning("FAWE arena reset failed for "+id+": "+e.getMessage());}finally{splitArenaLocks.remove("RESET:"+id);}};
        if(getConfig().getBoolean("arena-reset.async",true)) resetExecutor.submit(operation); else Bukkit.getScheduler().runTask(this,operation);
    }

    private void fawePaste(File file,Location target)throws Exception{
        Class<?> wf=Class.forName("com.sk89q.worldedit.WorldEdit");
        Object we=wf.getMethod("getInstance").invoke(null);
        Class<?> bukkitAdapter=Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Object weWorld=bukkitAdapter.getMethod("adapt",World.class).invoke(null,target.getWorld());
        Class<?> formats=Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
        Object fmt=formats.getMethod("findByFile",File.class).invoke(null,file);
        if(fmt==null)throw new IOException("Unknown schematic format: "+file);
        Object clipboard;
        try(InputStream in=new FileInputStream(file)){
            Method gr=fmt.getClass().getMethod("getReader",InputStream.class);
            Object reader=gr.invoke(fmt,in);
            clipboard=reader.getClass().getMethod("read").invoke(reader);
            try{reader.getClass().getMethod("close").invoke(reader);}catch(Exception ignored){}
        }
        Object builder=wf.getMethod("newEditSessionBuilder").invoke(we);
        Class<?> weWorldClass=Class.forName("com.sk89q.worldedit.world.World");
        builder=builder.getClass().getMethod("world",weWorldClass).invoke(builder,weWorld);
        Object edit=builder.getClass().getMethod("build").invoke(builder);
        Class<?> holderC=Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
        Object holder=holderC.getConstructor(Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard")).newInstance(clipboard);
        Class<?> bv=Class.forName("com.sk89q.worldedit.math.BlockVector3");
        Object pos=bv.getMethod("at",double.class,double.class,double.class).invoke(null,target.getX(),target.getY(),target.getZ());
        Object paste=holderC.getMethod("createPaste",Class.forName("com.sk89q.worldedit.EditSession")).invoke(holder,edit);
        paste=paste.getClass().getMethod("to",bv).invoke(paste,pos);
        if(getConfig().getBoolean("arena-reset.operations.ignore-air",false))paste=paste.getClass().getMethod("ignoreAirBlocks",boolean.class).invoke(paste,true);
        Object op=paste.getClass().getMethod("build").invoke(paste);
        Class<?> ops=Class.forName("com.sk89q.worldedit.function.operation.Operations");
        ops.getMethod("complete",Class.forName("com.sk89q.worldedit.function.operation.Operation")).invoke(null,op);
        try{edit.getClass().getMethod("close").invoke(edit);}catch(Exception ignored){}
    }

    private static final class CustomKitData { final List<String> inventory,armor; final String offhand; CustomKitData(List<String> i,List<String> a,String o){inventory=i;armor=a;offhand=o==null?"":o;} }
    private static final class Party { UUID leader; final Set<UUID> members=new LinkedHashSet<>(); final Map<UUID,Long> invites=new HashMap<>(); Party(UUID l){leader=l;} }
    private static final class SplitMatch { final String id,kit; final int arena; final Location spawnA,spawnB; boolean finished; final Map<UUID,Integer> team=new HashMap<>(); final Map<Integer,Integer> alive=new HashMap<>(); SplitMatch(String i,String k,int a,Location A,Location B){id=i;kit=k;arena=a;spawnA=A;spawnB=B;} }
    private static final class InventorySnapshot {
        ItemStack[] contents,armor,ender; ItemStack off,cursor; int level;float exp;double health;int food;float saturation;GameMode mode;boolean allowFlight,flying;
        static InventorySnapshot capture(Player p){
            InventorySnapshot s=new InventorySnapshot();s.contents=p.getInventory().getContents().clone();s.armor=p.getInventory().getArmorContents().clone();s.off=p.getInventory().getItemInOffHand().clone();s.ender=p.getEnderChest().getContents().clone();s.cursor=p.getOpenInventory().getCursor()==null?new ItemStack(Material.AIR):p.getOpenInventory().getCursor().clone();s.level=p.getLevel();s.exp=p.getExp();s.health=p.getHealth();s.food=p.getFoodLevel();s.saturation=p.getSaturation();s.mode=p.getGameMode();s.allowFlight=p.getAllowFlight();s.flying=p.isFlying();return s;
        }
        void restore(Player p){
            p.closeInventory();p.getInventory().setContents(contents);p.getInventory().setArmorContents(armor);p.getInventory().setItemInOffHand(off);p.getEnderChest().setContents(ender);p.getOpenInventory().setCursor(cursor);p.setLevel(level);p.setExp(exp);p.setFoodLevel(food);p.setSaturation(saturation);p.setGameMode(mode==null?GameMode.SURVIVAL:mode);p.setAllowFlight(allowFlight);p.setFlying(allowFlight&&flying);p.setHealth(Math.min(p.getMaxHealth(),Math.max(0.1,health)));
        }
    }
}