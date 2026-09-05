package com.karlo.orionffa.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String text = messages.getString(key, "<red>Missing message: " + key);
        if (!"prefix".equals(key)) text = messages.getString("prefix", "") + text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        sender.sendMessage(MiniMessage.miniMessage().deserialize(text));
    }

    public Component component(String text) {
        return MiniMessage.miniMessage().deserialize(text);
    }
}
