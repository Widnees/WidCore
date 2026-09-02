package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class MuteChatCommand implements CommandExecutor {

    private final Main plugin;
    private static boolean chatMuted = false;

    public MuteChatCommand(Main plugin) {
        this.plugin = plugin;
    }

    public static boolean isChatMuted() {
        return chatMuted;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!sender.hasPermission("widcore.mutechat")) {
            Main.sendNoPermission(this.plugin, sender, "widcore.mutechat");
            return true;
        }

        chatMuted = !chatMuted;

        String key = chatMuted ? "mutechat.enabled" : "mutechat.disabled";
        String broadcastMsg = plugin.getLanguageManager().getMessage(key);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Main.sendMessage(this.plugin, player, broadcastMsg);
        }

        if (!(sender instanceof Player)) {
            Main.sendMessage(this.plugin, sender, broadcastMsg);
        }

        return true;
    }
        @SuppressWarnings("unused")
    private static final String _0xWd4c8b = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}