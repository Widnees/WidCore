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
        @SuppressWarnings("unused")
    private static final String _0xW8b4d3 = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}