package org.widnees.widCore.module.modules.chat;

import org.bukkit.configuration.file.FileConfiguration;
import org.widnees.widCore.Main;
import org.widnees.widCore.listener.ChatFormatListener;
import org.widnees.widCore.module.Module;

public class ChatFormatModule implements Module {
    private final Main plugin;

    public ChatFormatModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Chat Formatting";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.chatformat", false);
    }

    @Override
    public void register() {
        FileConfiguration chatConfig = plugin.getConfigManager().getModuleConfig("chat");
        plugin.getServer().getPluginManager().registerEvents(new ChatFormatListener(plugin, chatConfig), plugin);
    }

    @Override
    public void unregister() {
    }
    
    @Override
    public java.util.List<String> getMissingOptionalDependencies() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            missing.add("PlaceholderAPI");
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            missing.add("LuckPerms");
        }
        return missing;
    }
}
