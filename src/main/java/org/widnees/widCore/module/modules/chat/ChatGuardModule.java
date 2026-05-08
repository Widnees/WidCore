package org.widnees.widCore.module.modules.chat;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.ChatGuardListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class ChatGuardModule implements Module {
    private final Main plugin;
    public ChatGuardModule(Main plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Chat Guard"; }
    @Override public boolean isEnabled() {
        return plugin.getConfig().getBoolean("chatguard.bannedword", false) ||
                plugin.getConfig().getBoolean("chatguard.spam", false) ||
                plugin.getConfig().getBoolean("chatguard.flood", false) ||
                plugin.getConfig().getBoolean("chatguard.advertisement", false);
    }
    @Override public void register() {
        plugin.getServer().getPluginManager().registerEvents(new ChatGuardListener(plugin, plugin.getChatGuardManager()), plugin);
    }
    @Override public void unregister() {}
}