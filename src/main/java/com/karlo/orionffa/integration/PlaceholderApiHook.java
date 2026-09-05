package com.karlo.orionffa.integration;
import com.karlo.orionffa.statistics.PlayerStatistics;
import com.karlo.orionffa.statistics.StatisticsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
public final class PlaceholderApiHook extends PlaceholderExpansion {
 private final JavaPlugin plugin; private final StatisticsManager statistics;
 public PlaceholderApiHook(JavaPlugin plugin,StatisticsManager statistics){this.plugin=plugin;this.statistics=statistics;}
 @Override public String getIdentifier(){return "orionffa";} @Override public String getAuthor(){return String.join(", ",plugin.getDescription().getAuthors());} @Override public String getVersion(){return plugin.getDescription().getVersion();} @Override public boolean persist(){return true;}
 @Override public String onRequest(OfflinePlayer player,String params){if(player==null)return ""; PlayerStatistics s=statistics.get(player.getUniqueId()); return switch(params.toLowerCase(java.util.Locale.ROOT)){case "kills"->""+s.kills();case "deaths"->""+s.deaths();case "streak","kill_streak"->""+s.killStreak();case "best_streak","best_kill_streak"->""+s.bestKillStreak();case "kd"->String.format(java.util.Locale.ROOT,"%.2f",s.kd());default->null;};}
}
