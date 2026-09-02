package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.MuteChatCommand;
import org.widnees.widCore.manager.ConfigManager;

public class ChatControlListener implements Listener {

    private final Main plugin;

    public ChatControlListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }

        if (!MuteChatCommand.isChatMuted()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("widcore.mutechat.bypass")) {
            return;
        }

        event.setCancelled(true);
        Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("mutechat.blocked"));
    }
        @SuppressWarnings("unused")
    private static final String _0xWe5b9c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}