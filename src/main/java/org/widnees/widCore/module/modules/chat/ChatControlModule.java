package org.widnees.widCore.module.modules.chat;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.ClearChatCommand;
import org.widnees.widCore.command.MuteChatCommand;
import org.widnees.widCore.listener.ChatControlListener;
import org.widnees.widCore.module.Module;

public class ChatControlModule implements Module {

    private final Main plugin;

    public ChatControlModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Chat Control";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.chatcontrol", true);
    }

    @Override
    public void register() {
        String clearDesc = plugin.getLanguageManager().getMessage("clearchat.description");
        String clearUsage = plugin.getLanguageManager().getMessage("clearchat.usage_args");
        String muteDesc = plugin.getLanguageManager().getMessage("mutechat.description");
        String muteUsage = plugin.getLanguageManager().getMessage("mutechat.usage_args");

        plugin.getModuleManager().registerCommand(this, "clearchat", clearDesc, clearUsage,
                "widcore.clearchat", null, new ClearChatCommand(plugin));
        plugin.getModuleManager().registerCommand(this, "mutechat", muteDesc, muteUsage,
                "widcore.mutechat", null, new MuteChatCommand(plugin));

        plugin.getServer().getPluginManager().registerEvents(new ChatControlListener(plugin), plugin);
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String _0xWf6a1d = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}