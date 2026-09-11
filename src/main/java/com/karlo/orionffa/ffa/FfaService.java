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
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class FfaService {
    private final ConfigManager config; private final PlayerSessionManager sessions; private final KitManager kits; private final ArenaManager arenas; private final TeleportService teleports; private final KitPersistenceManager customKits; private final ArenaResetService resets;
    private Consumer<Player> lobbyMenuApplier = player -> { };
    public FfaService(ConfigManager config, PlayerSessionManager sessions, KitManager kits, ArenaManager arenas, TeleportService teleports, KitPersistenceManager customKits, ArenaResetService resets) { this.config=config; this.sessions=sessions; this.kits=kits; this.arenas=arenas; this.teleports=teleports; this.customKits=customKits; this.resets=resets; }
    public void setLobbyMenuApplier(Consumer<Player> applier) { lobbyMenuApplier=applier==null?player->{ }:applier; }
    public Optional<Location> lobbyLocation() { if(config.runtime().lobby()==null)return Optional.empty(); return config.runtime().lobby().resolve(); }
    public ServiceResult enterLobby(Player player) {
        if(!config.runtime().ffaEnabled())return ServiceResult.fail("ffa-disabled"); if(config.runtime().lobby()==null)return ServiceResult.fail("lobby-not-set");
        Optional<PlayerSession> existing=sessions.get(player.getUniqueId());
        if(existing.isPresent()) { if(existing.get().state()==FfaState.SPECTATING)return stopSpectating(player); if(existing.get().state()==FfaState.FFA){if(existing.get().arenaId()==null){resetLobbyPresentation(player);lobbyMenuApplier.accept(player);return ServiceResult.ok("already-in-ffa");}return leaveToLobby(player);} if(existing.get().state()==FfaState.EDITING_KIT)return ServiceResult.fail("already-editing-kit"); if(existing.get().state()==FfaState.PARTY||existing.get().state()==FfaState.PARTY_MATCH||existing.get().state()==FfaState.SPLIT_MATCH||existing.get().state()==FfaState.RECOVERING)return ServiceResult.fail("state-locked"); }
        PlayerSession session=sessions.enter(player); if(!teleports.teleport(player,config.runtime().lobby())){sessions.remove(player.getUniqueId());return ServiceResult.fail("world-unavailable");} resetLobbyPresentation(player); session.state(FfaState.FFA); lobbyMenuApplier.accept(player); return ServiceResult.ok("entered-lobby");
    }
    public ServiceResult editKit(Player player,String kitId) {
        if(!config.runtime().ffaEnabled())return ServiceResult.fail("ffa-disabled"); if(config.runtime().editKit()==null)return ServiceResult.fail("edit-kit-not-set"); Optional<KitDefinition> kit=kits.find(kitId); if(kit.isEmpty())return ServiceResult.fail("kit-unavailable");
        PlayerSession session=sessions.enter(player); FfaState previousState=session.state(); String previousArena=session.arenaId(); if(!teleports.teleport(player,config.runtime().editKit())){if(previousState==FfaState.OUTSIDE)sessions.remove(player.getUniqueId());return ServiceResult.fail("world-unavailable");} if(previousArena!=null)arenas.leave(previousArena,player.getUniqueId()); applyKit(player,kit.get(),customKits.find(player.getUniqueId(),kit.get().id()).orElse(null)); if(player.hasPermission("orionffa.admin"))player.setGameMode(GameMode.CREATIVE); session.kitId(kit.get().id());session.arenaId(null);session.spectatorTarget(null);session.state(FfaState.EDITING_KIT); return ServiceResult.ok("kit-editing",Map.of("kit",kit.get().id()));
    }
    public ServiceResult saveEditedKit(Player player,String kitId){Optional<PlayerSession> s=sessions.get(player.getUniqueId());if(s.isEmpty()||s.get().state()!=FfaState.EDITING_KIT)return ServiceResult.fail("not-editing-kit");if(kitId==null||!kitId.equalsIgnoreCase(s.get().kitId()))return ServiceResult.fail("kit-unavailable");customKits.saveFromPlayer(player,s.get().kitId());return ServiceResult.ok("kit-saved",Map.of("kit",s.get().kitId()));}
    public ServiceResult joinKit(Player player,String kitId){if(!config.runtime().ffaEnabled())return ServiceResult.fail("ffa-disabled");Optional<KitDefinition> k=kits.find(kitId);if(k.isEmpty())return ServiceResult.fail("kit-unavailable");Optional<Arena> a=arenas.select(k.get().id,config.runtime().selectionStrategy());return a.filter(x->!resets.isResetting(x.id())).map(x->join(player,k.get(),x)).orElseGet(()->ServiceResult.fail("arena-unavailable"));}
    public ServiceResult joinKitAt(Player player,String kitId,String arenaId){if(!config.runtime().ffaEnabled())return ServiceResult.fail("ffa-disabled");Optional<KitDefinition> k=kits.find(kitId);Optional<Arena> a=arenas.get(arenaId);if(k.isEmpty())return ServiceResult.fail("kit-unavailable");if(a.isEmpty()||resets.isResetting(a.get().id())||!arenas.availableFor(k.get().id()).contains(a))return ServiceResult.fail("arena-unavailable");return join(player,k.get(),a.get());}
    public List<Arena> availableArenas(String kitId){return arenas.availableFor(kitId).stream().filter(a->!resets.isResetting(a.id())).toList();}
    private ServiceResult join(Player player,KitDefinition kit,Arena arena){
        PlayerSession s=sessions.enter(player);String pk=s.kitId(),pa=s.arenaId();FfaState ps=s.state();UUID id=player.getUniqueId();if(!arenas.reserve(arena,id))return ServiceResult.fail("arena-unavailable");
        try{if(!arena.claim(id))return ServiceResult.fail("arena-unavailable");if(!teleports.teleport(player,arena.spawn())){arenas.leave(arena.id(),id);return ServiceResult.fail("world-unavailable");}applyKit(player,kit,customKits.find(id,kit.id()).orElse(null));if(pa!=null&&!pa.equals(arena.id()))arenas.leave(pa,id);s.kitId(kit.id());s.arenaId(arena.id());s.spectatorTarget(null);s.state(FfaState.FFA);return ServiceResult.ok("joined-kit",Map.of("kit",kit.id(),"arena",arena.id()));}
        catch(RuntimeException failure){arenas.leave(arena.id(),id);s.kitId(pk);s.arenaId(pa);s.spectatorTarget(null);s.state(ps);if(ps==FfaState.OUTSIDE){restore(player,s.snapshot());sessions.remove(id);}else if(ps==FfaState.FFA&&pk!=null&&pa!=null){Optional<KitDefinition> ok=kits.find(pk);Optional<Arena> oa=arenas.get(pa);if(ok.isPresent()&&oa.isPresent()&&teleports.teleport(player,oa.get().spawn()))applyKit(player,ok.get(),customKits.find(id,ok.get().id()).orElse(null));else restore(player,s.snapshot());}else restore(player,s.snapshot());return ServiceResult.fail("action-failed");}
        finally{arenas.release(arena,id);}
    }
    public ServiceResult leave(Player player){Optional<PlayerSession> f=sessions.get(player.getUniqueId());if(f.isEmpty())return ServiceResult.ok("left-ffa");if(f.get().state()==FfaState.SPECTATING)return stopSpectating(player);PlayerSession s=f.get();if(s.state()==FfaState.EDITING_KIT)return leaveToLobby(player);PlayerSnapshot snap=s.snapshot();if(!teleports.teleport(player,snap.location()))return ServiceResult.fail("world-unavailable");restore(player,snap);if(s.arenaId()!=null)arenas.leave(s.arenaId(),player.getUniqueId());sessions.remove(player.getUniqueId());return ServiceResult.ok("left-ffa");}
    public ServiceResult leaveKitEditor(Player player){Optional<PlayerSession> f=sessions.get(player.getUniqueId());if(f.isEmpty()||f.get().state()!=FfaState.EDITING_KIT)return ServiceResult.fail("not-editing-kit");return leaveToLobby(player);}
    public ServiceResult leaveToLobby(Player player){
        targetLeft(player.getUniqueId());
        if(config.runtime().lobby()==null)return ServiceResult.fail("lobby-not-set");if(!teleports.teleport(player,config.runtime().lobby()))return ServiceResult.fail("world-unavailable");Optional<PlayerSession> f=sessions.get(player.getUniqueId());if(f.isPresent()&&f.get().arenaId()!=null)arenas.leave(f.get().arenaId(),player.getUniqueId());
        player.getInventory().clear();player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);player.getInventory().setItemInOffHand(null);player.setGameMode(GameMode.SURVIVAL);player.setHealth(player.getMaxHealth());player.setFoodLevel(20);player.setFireTicks(0);player.setLevel(0);player.setExp(0);player.setAllowFlight(false);player.setFlying(false);player.setFallDistance(0);player.setInvisible(false);player.setInvulnerable(false);player.setCollidable(true);player.setSilent(false);player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        PlayerSession s=f.orElseGet(()->sessions.enter(player));s.kitId(null);s.arenaId(null);s.spectatorTarget(null);s.state(FfaState.FFA);lobbyMenuApplier.accept(player);return ServiceResult.ok("entered-lobby");
    }
    public ServiceResult startSpectating(Player spectator,Player target){
        if(spectator.equals(target))return ServiceResult.fail("spectate-self");Optional<PlayerSession> ts=sessions.get(target.getUniqueId());if(ts.isEmpty()||ts.get().state()!=FfaState.FFA||ts.get().arenaId()==null)return ServiceResult.fail("spectate-unavailable");if(arenas.get(ts.get().arenaId()).isEmpty())return ServiceResult.fail("spectate-unavailable");
        PlayerSession s=sessions.enter(spectator);if(s.arenaId()!=null)arenas.leave(s.arenaId(),spectator.getUniqueId());spectator.getInventory().clear();spectator.setInvisible(false);spectator.setInvulnerable(false);spectator.setCollidable(true);spectator.setSilent(false);spectator.setAllowFlight(false);spectator.setFlying(false);teleports.prepare(spectator);spectator.setGameMode(GameMode.SPECTATOR);spectator.setSpectatorTarget(target);s.spectatorTarget(target.getUniqueId());s.state(FfaState.SPECTATING);spectator.sendMessage(messagesForSpectate(target));return ServiceResult.ok("spectating",Map.of("player",target.getName()));
    }
    public ServiceResult stopSpectating(Player player){Optional<PlayerSession> f=sessions.get(player.getUniqueId());if(f.isEmpty()||f.get().state()!=FfaState.SPECTATING)return ServiceResult.fail("spectate-unavailable");player.setSpectatorTarget(null);player.setGameMode(GameMode.SURVIVAL);player.setInvisible(false);player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);return leaveToLobby(player);}
    public void recover(Player player){sessions.get(player.getUniqueId()).ifPresent(s->{if(s.state()==FfaState.SPECTATING){s.spectatorTarget(null);return;}if(s.kitId()==null||s.arenaId()==null)return;kits.find(s.kitId()).ifPresent(k->{applyKit(player,k,customKits.find(player.getUniqueId(),k.id()).orElse(null));teleports.normalize(player);s.state(FfaState.FFA);});});}
    public void cleanup(Player player){sessions.get(player.getUniqueId()).ifPresent(s->{if(s.arenaId()!=null)arenas.leave(s.arenaId(),player.getUniqueId());});sessions.remove(player.getUniqueId());}
    public void targetLeft(UUID targetId){for(PlayerSession s:sessions.active()){if(!targetId.equals(s.spectatorTarget()))continue;Player p=org.bukkit.Bukkit.getPlayer(s.playerId());if(p!=null)stopSpectating(p);}}
    private net.kyori.adventure.text.Component messagesForSpectate(Player target){return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<green>You are now spectating <white>"+target.getName()+"<green>.<newline><yellow>To leave: <white>/offa spectate leave<yellow> or <white>/offa lobby<yellow>.");}
    private void resetLobbyPresentation(Player player){player.setGameMode(GameMode.SURVIVAL);player.setInvisible(false);player.setInvulnerable(false);player.setCollidable(true);player.setSilent(false);player.setAllowFlight(false);player.setFlying(false);player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);}
    private static void applyKit(Player player,KitDefinition kit,PlayerKitData custom){player.getInventory().clear();if(custom==null){player.getInventory().setArmorContents(kit.armor().clone());player.getInventory().setItemInOffHand(kit.offhand()==null?null:kit.offhand().clone());kit.inventory().forEach((slot,item)->player.getInventory().setItem(slot,item.clone()));}else{player.getInventory().setContents(custom.inventory());player.getInventory().setArmorContents(custom.armor());player.getInventory().setItemInOffHand(custom.offhand());}player.setGameMode(kit.gameMode());player.setHealth(player.getMaxHealth());player.setFoodLevel(20);player.setFireTicks(0);}
    private static void restore(Player player,PlayerSnapshot snapshot){player.getInventory().setContents(snapshot.inventory());player.getInventory().setArmorContents(snapshot.armor());player.getInventory().setItemInOffHand(snapshot.offhand());player.setGameMode(snapshot.gameMode());player.setLevel(snapshot.level());player.setExp(snapshot.experience());player.setFoodLevel(snapshot.foodLevel());player.getActivePotionEffects().forEach(e->player.removePotionEffect(e.getType()));snapshot.effects().forEach(player::addPotionEffect);player.setHealth(Math.min(player.getMaxHealth(),snapshot.health()));player.setAllowFlight(snapshot.allowFlight());player.setFlying(snapshot.flying()&&snapshot.allowFlight());player.setGliding(snapshot.gliding());player.setSwimming(snapshot.swimming());player.setSprinting(snapshot.sprinting());player.setSneaking(snapshot.sneaking());player.getInventory().setHeldItemSlot(snapshot.heldItemSlot());player.setVelocity(snapshot.velocity()==null?new Vector():snapshot.velocity().clone());}
}