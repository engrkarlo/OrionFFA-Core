package com.karlo.orionffa.storage;
import com.karlo.orionffa.kit.KitManager;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File; import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicInteger;
public final class StorageMigrationService {
 private final JavaPlugin plugin; private final KitManager kits; private final StorageProvider target;
 public StorageMigrationService(JavaPlugin plugin,KitManager kits,StorageProvider target){this.plugin=plugin;this.kits=kits;this.target=target;}
 public CompletableFuture<Result> migrateYamlToTarget(){
  if(!(target instanceof MySqlStorageProvider))return CompletableFuture.completedFuture(new Result(false,0,"Migration target must be MySQL."));
  File dir=new File(plugin.getDataFolder(),"playerdata"); if(!dir.isDirectory())return CompletableFuture.completedFuture(new Result(true,0,"No YAML player data found."));
  List<UUID> ids=new ArrayList<>(); File[] files=dir.listFiles((d,n)->n.endsWith(".yml")); if(files!=null)for(File f:files)try{ids.add(UUID.fromString(f.getName().substring(0,f.getName().length()-4)));}catch(IllegalArgumentException ignored){}
  if(ids.isEmpty())return CompletableFuture.completedFuture(new Result(true,0,"No YAML player data found."));
  YamlStorageProvider source=new YamlStorageProvider(plugin); AtomicInteger done=new AtomicInteger(); List<CompletableFuture<Void>> all=new ArrayList<>();
  for(UUID id:ids){List<CompletableFuture<Void>> ops=new ArrayList<>(); ops.add(source.loadStatistics(id).thenCompose(s->target.saveStatistics(id,s))); for(String kit:kits.names())ops.add(source.loadKit(id,kit).thenCompose(d->(d.inventory().length==0&&d.armor().length==0&&d.offhand()==null)?CompletableFuture.completedFuture(null):target.saveKit(id,kit,d))); all.add(CompletableFuture.allOf(ops.toArray(CompletableFuture[]::new)).thenRun(done::incrementAndGet));}
  return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).thenApply(v->new Result(true,done.get(),"Migration completed.")).exceptionally(e->new Result(false,done.get(),"Migration failed: "+e.getMessage())).whenComplete((v,e)->source.close());
 }
 public record Result(boolean success,int players,String message){}
}
