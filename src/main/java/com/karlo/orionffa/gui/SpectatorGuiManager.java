package com.karlo.orionffa.gui;

import com.karlo.orionffa.arena.ArenaManager;
import com.karlo.orionffa.ffa.FfaService;
import com.karlo.orionffa.ffa.ServiceResult;
import com.karlo.orionffa.message.MessageService;
import com.karlo.orionffa.player.FfaState;
import com.karlo.orionffa.player.PlayerSession;
import com.karlo.orionffa.player.PlayerSessionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.List;
import java.util.UUID;

/** Owns only the spectator selector. Native Bukkit spectator mode owns the camera and hotbar. */
public final class SpectatorGuiManager {
    private final JavaPlugin plugin; private final MessageService messages; private final FfaService ffa; private final PlayerSessionManager sessions; private final ArenaManager arenas;
    private final NamespacedKey actionKey; private final NamespacedKey targetKey;
    public SpectatorGuiManager(JavaPlugin plugin, MessageService messages, FfaService ffa, PlayerSessionManager sessions, ArenaManager arenas, LobbyMenuManager lobbyMenus) { this.plugin=plugin; this.messages=messages; this.ffa=ffa; this.sessions=sessions; this.arenas=arenas; this.actionKey=new NamespacedKey(plugin,"spectator-action"); this.targetKey=new NamespacedKey(plugin,"spectator-target"); }
    public boolean owns(Inventory inventory){return inventory!=null&&inventory.getHolder(false) instanceof Holder;}
    public void open(Player player){YamlConfiguration config=loadConfig();ConfigurationSection menu=config.getConfigurationSection("menus.spectator_selector");if(menu==null)return;int rows=Math.max(1,Math.min(6,menu.getInt("rows",6)));Inventory inventory=Bukkit.createInventory(new Holder(),rows*9,messages.component(menu.getString("title","<dark_gray>Select a Player</dark_gray>")));fill(inventory,menu.getConfigurationSection("filler"));ConfigurationSection back=menu.getConfigurationSection("items.back");addConfigured(inventory,back,"back","");int backSlot=back==null?-1:back.getInt("slot",-1);int slot=0;for(PlayerSession session:sessions.active()){if(session.playerId().equals(player.getUniqueId())||!isValidSession(session))continue;Player target=Bukkit.getPlayer(session.playerId());if(target==null||!target.isOnline()||target.isDead())continue;while(slot<inventory.getSize()&&slot==backSlot)slot++;if(slot>=inventory.getSize())break;inventory.setItem(slot++,item(Material.PLAYER_HEAD,"<light_purple>"+target.getName(),List.of("<gray>Arena: <white>"+session.arenaId(),"<yellow>Click to spectate."),"spectate",target.getUniqueId().toString()));}player.openInventory(inventory);}
    public void handle(Player player,ItemStack item){if(item==null||!item.hasItemMeta())return;var data=item.getItemMeta().getPersistentDataContainer();String action=data.get(actionKey,PersistentDataType.STRING);String target=data.get(targetKey,PersistentDataType.STRING);if(action==null)return;if("back".equals(action)){player.closeInventory();return;}if(!"spectate".equals(action))return;try{Player targetPlayer=Bukkit.getPlayer(UUID.fromString(target==null?"":target));if(targetPlayer==null||!isValidTarget(targetPlayer)){messages.send(player,"spectate-unavailable");return;}ServiceResult result=ffa.startSpectating(player,targetPlayer);if(result.success())player.closeInventory();else messages.send(player,result.messageKey());}catch(IllegalArgumentException ignored){messages.send(player,"spectate-unavailable");}}
    private boolean isValidSession(PlayerSession session){return session.state()==FfaState.FFA&&session.arenaId()!=null&&!session.arenaId().isBlank()&&arenas.get(session.arenaId()).map(a->a.enabled()).orElse(false);}
    public boolean isValidTarget(Player target){return sessions.get(target.getUniqueId()).map(this::isValidSession).orElse(false);}
    public boolean isSpectating(Player player){return sessions.get(player.getUniqueId()).map(s->s.state()==FfaState.SPECTATING).orElse(false);}
    public boolean isSpectatorItem(ItemStack item){return false;}
    public String spectatorAction(ItemStack item){return "";}
    /** Compatibility hook retained for old callers. Native spectator mode is established by FfaService. */
    public void enterSpectatingMode(Player player){if(!isSpectating(player))return;Player target=currentTarget(player);player.setGameMode(org.bukkit.GameMode.SPECTATOR);if(target!=null&&target.isOnline()&&!target.isDead())player.setSpectatorTarget(target);}
    public void refresh(Player player){if(!isSpectating(player))return;Player target=currentTarget(player);player.setGameMode(org.bukkit.GameMode.SPECTATOR);if(target!=null&&target.isOnline()&&!target.isDead())player.setSpectatorTarget(target);}
    private Player currentTarget(Player player){return sessions.get(player.getUniqueId()).map(PlayerSession::spectatorTarget).map(Bukkit::getPlayer).filter(t->t!=null&&t.isOnline()&&!t.isDead()&&isValidTarget(t)).orElse(null);}
    public void applyHotbar(Player player){/* Native spectator intentionally owns the hotbar. */}
    public void exit(Player player){if(!isSpectating(player))return;ServiceResult result=ffa.stopSpectating(player);if(!result.success())messages.send(player,result.messageKey());}
    private ItemStack item(Material material,String name,List<String> lore,String action,String target){ItemStack item=new ItemStack(material==null?Material.BARRIER:material);ItemMeta meta=item.getItemMeta();meta.displayName(messages.component(name));meta.lore(lore.stream().map(messages::component).toList());meta.getPersistentDataContainer().set(actionKey,PersistentDataType.STRING,action);meta.getPersistentDataContainer().set(targetKey,PersistentDataType.STRING,target);item.setItemMeta(meta);return item;}
    private void addConfigured(Inventory inventory,ConfigurationSection section,String action,String target){if(section==null)return;int slot=section.getInt("slot",-1);if(slot<0||slot>=inventory.getSize())return;Material material=Material.matchMaterial(section.getString("material","BARRIER"));inventory.setItem(slot,item(material,section.getString("name","<red>Back"),section.getStringList("lore"),action,target));}
    private void fill(Inventory inventory,ConfigurationSection filler){if(filler==null||!filler.getBoolean("enabled",true))return;Material material=Material.matchMaterial(filler.getString("material","GRAY_STAINED_GLASS_PANE"));if(material==null)material=Material.GRAY_STAINED_GLASS_PANE;ItemStack pane=item(material,filler.getString("name"," "),filler.getStringList("lore"),"none","");for(int slot=0;slot<inventory.getSize();slot++)inventory.setItem(slot,pane.clone());}
    private YamlConfiguration loadConfig(){return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"guis.yml"));}
    private static final class Holder implements InventoryHolder{@Override public Inventory getInventory(){return null;}}
}
